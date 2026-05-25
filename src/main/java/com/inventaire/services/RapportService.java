package com.inventaire.services;

import com.inventaire.dao.DatabaseConnection;
import com.inventaire.dao.StockDAO;
import com.inventaire.models.StockActuel;
import com.inventaire.models.Transaction;
import com.inventaire.utils.DateUtil;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de génération de rapports (PDF via iText 7 et Excel via Apache POI).
 *
 * <p>Génère trois types de rapports :
 * <ul>
 *   <li>État des stocks : snapshot complet par entrepôt</li>
 *   <li>Mouvements : historique des transactions sur une période</li>
 *   <li>Audit blockchain : liste avec statuts de vérification</li>
 * </ul>
 *
 * @author Système Inventaire
 * @version 1.0.0
 */
public class RapportService {

    private static final Logger LOG = LoggerFactory.getLogger(RapportService.class);

    /** Couleur principale bleu foncé (en RGB 0-1). */
    private static final DeviceRgb COULEUR_PRINCIPALE = new DeviceRgb(26, 35, 126);
    /** Couleur secondaire bleu clair. */
    private static final DeviceRgb COULEUR_EN_TETE = new DeviceRgb(63, 81, 181);
    /** Couleur texte blanc. */
    private static final DeviceRgb BLANC = new DeviceRgb(255, 255, 255);
    /** Couleur fond rangée alternée. */
    private static final DeviceRgb GRIS_CLAIR = new DeviceRgb(245, 245, 250);

    private final StockDAO stockDAO;
    private final InventaireService inventaireService;

    /**
     * Constructeur.
     *
     * @param db                Instance de connexion PostgreSQL
     * @param inventaireService Service inventaire
     */
    public RapportService(DatabaseConnection db, InventaireService inventaireService) {
        this.stockDAO = new StockDAO(db);
        this.inventaireService = inventaireService;
    }

    // ================================================================
    // Génération PDF
    // ================================================================

    /**
     * Génère un rapport PDF d'état des stocks.
     *
     * @param cheminFichier Chemin du fichier PDF à créer
     * @param entrepotId    UUID de l'entrepôt (null = tous les entrepôts)
     * @throws IOException en cas d'erreur d'écriture
     */
    public void genererPdfEtatStocks(String cheminFichier, java.util.UUID entrepotId)
            throws IOException {

        LOG.info("Génération rapport PDF état des stocks → {}", cheminFichier);

        List<StockActuel> stocks = entrepotId != null
            ? stockDAO.getStockParEntrepot(entrepotId)
            : stockDAO.getToutLeStock();

        try (PdfWriter writer = new PdfWriter(cheminFichier);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc, PageSize.A4)) {

            document.setMargins(30, 30, 30, 30);

            // ---- En-tête du rapport ----
            ajouterEnteteRapport(document, "ÉTAT DES STOCKS",
                "Généré le " + DateUtil.formaterDateHeure(LocalDateTime.now()));

            // ---- Tableau des stocks ----
            Table table = creerTableau(new float[]{2, 3, 2, 1.5f, 1.5f, 1.5f});
            ajouterEnTeteTableau(table, "Référence", "Produit", "Entrepôt",
                "Quantité", "Seuil Critique", "Statut");

            double valeurTotale = 0;
            int ligne = 0;

            for (StockActuel stock : stocks) {
                boolean rangeeAlternee = (ligne % 2 == 1);
                DeviceRgb couleurFond = rangeeAlternee ? GRIS_CLAIR :
                    new DeviceRgb(255, 255, 255);

                ajouterCellule(table, stock.getProduitReference(), couleurFond, TextAlignment.LEFT);
                ajouterCellule(table, stock.getProduitNom(), couleurFond, TextAlignment.LEFT);
                ajouterCellule(table, stock.getEntrepotNom(), couleurFond, TextAlignment.LEFT);
                ajouterCellule(table, String.valueOf(stock.getQuantite()), couleurFond, TextAlignment.CENTER);
                ajouterCellule(table, String.valueOf(stock.getSeuilCritique()), couleurFond, TextAlignment.CENTER);

                // Colonne statut avec couleur
                String statut = stock.getStatut();
                DeviceRgb couleurStatut = switch (statut) {
                    case "CRITIQUE" -> new DeviceRgb(198, 40, 40);
                    case "FAIBLE"   -> new DeviceRgb(230, 119, 0);
                    default         -> new DeviceRgb(46, 125, 50);
                };
                Cell cellStatut = new Cell()
                    .add(new Paragraph(statut)
                        .setFontColor(BLANC)
                        .setFontSize(8)
                        .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(couleurStatut)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(4);
                table.addCell(cellStatut);

                ligne++;
            }

            document.add(table);

            // ---- Total ----
            double valeur = stockDAO.getValeurTotaleStock();
            document.add(new Paragraph(
                "\nValeur totale du stock : " + String.format("%.2f €", valeur))
                .setFontColor(COULEUR_PRINCIPALE)
                .setFontSize(12)
                .setBold()
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(10));

            // Pied de page
            ajouterPiedPage(document);
        }

        LOG.info("Rapport PDF état des stocks généré avec succès.");
    }

    /**
     * Génère un rapport PDF des mouvements de stock.
     *
     * @param cheminFichier Chemin du fichier PDF
     * @param transactions  Liste des transactions à inclure
     * @param dateDebut     Date de début de la période
     * @param dateFin       Date de fin de la période
     * @throws IOException en cas d'erreur d'écriture
     */
    public void genererPdfMouvements(String cheminFichier, List<Transaction> transactions,
                                      LocalDate dateDebut, LocalDate dateFin)
            throws IOException {

        LOG.info("Génération rapport PDF mouvements ({} transactions) → {}",
            transactions.size(), cheminFichier);

        try (PdfWriter writer = new PdfWriter(cheminFichier);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc, PageSize.A4.rotate())) {

            document.setMargins(20, 20, 20, 20);

            ajouterEnteteRapport(document, "MOUVEMENTS DE STOCK",
                "Période : " + DateUtil.formaterDate(dateDebut)
                + " → " + DateUtil.formaterDate(dateFin)
                + "  |  " + transactions.size() + " transaction(s)");

            Table table = creerTableau(new float[]{2.5f, 1.5f, 2, 1.2f, 1, 1, 2, 2, 1.5f});
            ajouterEnTeteTableau(table, "Date/Heure", "Type", "Produit", "Quantité",
                "Avant", "Après", "Opérateur", "Entrepôt", "Statut");

            int ligne = 0;
            for (Transaction tx : transactions) {
                DeviceRgb couleurFond = (ligne % 2 == 1) ? GRIS_CLAIR :
                    new DeviceRgb(255, 255, 255);

                ajouterCellule(table,
                    DateUtil.formaterDateHeure(tx.getEnregistreLe()),
                    couleurFond, TextAlignment.LEFT);

                // Type avec couleur
                DeviceRgb couleurType = switch (tx.getType()) {
                    case "ENTREE"    -> new DeviceRgb(46, 125, 50);
                    case "SORTIE"    -> new DeviceRgb(198, 40, 40);
                    case "TRANSFERT" -> new DeviceRgb(21, 101, 192);
                    default          -> new DeviceRgb(97, 97, 97);
                };
                table.addCell(new Cell()
                    .add(new Paragraph(tx.getIconeType() + " " + tx.getType())
                        .setFontColor(couleurType).setFontSize(7))
                    .setBackgroundColor(couleurFond)
                    .setBorder(Border.NO_BORDER).setPadding(3));

                ajouterCellule(table,
                    tx.getProduitReference() + "\n" + tx.getProduitNom(),
                    couleurFond, TextAlignment.LEFT);
                ajouterCellule(table, String.valueOf(tx.getQuantite()),
                    couleurFond, TextAlignment.CENTER);
                ajouterCellule(table,
                    tx.getQuantiteAvant() != null ? String.valueOf(tx.getQuantiteAvant()) : "—",
                    couleurFond, TextAlignment.CENTER);
                ajouterCellule(table,
                    tx.getQuantiteApres() != null ? String.valueOf(tx.getQuantiteApres()) : "—",
                    couleurFond, TextAlignment.CENTER);
                ajouterCellule(table,
                    tx.getOperateurNom() != null ? tx.getOperateurNom() : "—",
                    couleurFond, TextAlignment.LEFT);
                ajouterCellule(table,
                    tx.getEntrepotSourceNom() != null ? tx.getEntrepotSourceNom() :
                    (tx.getEntrepotDestinationNom() != null ? tx.getEntrepotDestinationNom() : "—"),
                    couleurFond, TextAlignment.LEFT);
                ajouterCellule(table, tx.getStatut(), couleurFond, TextAlignment.CENTER);

                ligne++;
            }

            document.add(table);
            ajouterPiedPage(document);
        }

        LOG.info("Rapport PDF mouvements généré.");
    }

    // ================================================================
    // Génération Excel
    // ================================================================

    /**
     * Génère un rapport Excel d'état des stocks.
     *
     * @param cheminFichier Chemin du fichier Excel (.xlsx)
     * @throws IOException en cas d'erreur d'écriture
     */
    public void genererExcelEtatStocks(String cheminFichier) throws IOException {
        LOG.info("Génération rapport Excel état des stocks → {}", cheminFichier);

        List<StockActuel> stocks = stockDAO.getToutLeStock();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(cheminFichier)) {

            Sheet sheet = workbook.createSheet("État des Stocks");

            // ---- Styles ----
            CellStyle styleEnTete = creerStyleEnTete(workbook);
            CellStyle styleAlterne = creerStyleAlterne(workbook);
            CellStyle styleNormal = creerStyleNormal(workbook);
            CellStyle styleCritique = creerStyleStatut(workbook, new byte[]{(byte)198, 40, 40});
            CellStyle styleFaible = creerStyleStatut(workbook, new byte[]{(byte)230, 119, 0});
            CellStyle styleOk = creerStyleStatut(workbook, new byte[]{46, 125, 50});

            // ---- Titre ----
            Row titreLigne = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titreCellule = titreLigne.createCell(0);
            titreCellule.setCellValue("ÉTAT DES STOCKS — " +
                DateUtil.formaterDate(LocalDate.now()));
            CellStyle styleTitre = workbook.createCellStyle();
            Font fontTitre = workbook.createFont();
            fontTitre.setBold(true);
            fontTitre.setFontHeightInPoints((short) 14);
            styleTitre.setFont(fontTitre);
            titreCellule.setCellStyle(styleTitre);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            // ---- En-têtes colonnes ----
            Row rowEnTete = sheet.createRow(2);
            String[] entetes = {"Référence", "Produit", "Entrepôt", "Quantité", "Seuil Critique", "Statut"};
            for (int i = 0; i < entetes.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = rowEnTete.createCell(i);
                cell.setCellValue(entetes[i]);
                cell.setCellStyle(styleEnTete);
            }

            // ---- Données ----
            int numLigne = 3;
            for (StockActuel stock : stocks) {
                Row row = sheet.createRow(numLigne);
                boolean alternee = (numLigne % 2 == 0);
                CellStyle style = alternee ? styleAlterne : styleNormal;

                row.createCell(0).setCellValue(stock.getProduitReference());
                row.createCell(1).setCellValue(stock.getProduitNom());
                row.createCell(2).setCellValue(stock.getEntrepotNom());
                row.createCell(3).setCellValue(stock.getQuantite());
                row.createCell(4).setCellValue(stock.getSeuilCritique());

                row.getCell(0).setCellStyle(style);
                row.getCell(1).setCellStyle(style);
                row.getCell(2).setCellStyle(style);
                row.getCell(3).setCellStyle(style);
                row.getCell(4).setCellStyle(style);

                // Statut avec couleur
                org.apache.poi.ss.usermodel.Cell cellStatut = row.createCell(5);
                cellStatut.setCellValue(stock.getStatut());
                cellStatut.setCellStyle(switch (stock.getStatut()) {
                    case "CRITIQUE" -> styleCritique;
                    case "FAIBLE"   -> styleFaible;
                    default         -> styleOk;
                });

                numLigne++;
            }

            // ---- Ligne total ----
            numLigne++;
            Row rowTotal = sheet.createRow(numLigne);
            rowTotal.createCell(0).setCellValue("VALEUR TOTALE STOCK :");
            rowTotal.createCell(3).setCellValue(stockDAO.getValeurTotaleStock());

            // ---- Ajustement colonnes ----
            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(fos);
        }

        LOG.info("Rapport Excel état des stocks généré.");
    }

    /**
     * Génère un rapport Excel des mouvements de stock.
     *
     * @param cheminFichier Chemin du fichier Excel
     * @param transactions  Liste des transactions
     * @throws IOException en cas d'erreur
     */
    public void genererExcelMouvements(String cheminFichier,
                                        List<Transaction> transactions) throws IOException {
        LOG.info("Génération rapport Excel mouvements ({} lignes) → {}",
            transactions.size(), cheminFichier);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(cheminFichier)) {

            Sheet sheet = workbook.createSheet("Mouvements");
            CellStyle styleEnTete = creerStyleEnTete(workbook);

            String[] entetes = {
                "Date/Heure", "ID Blockchain", "Type", "Produit",
                "Référence", "Quantité", "Avant", "Après",
                "Opérateur", "Entrepôt Source", "Entrepôt Dest", "Statut"
            };

            Row rowEnTete = sheet.createRow(0);
            for (int i = 0; i < entetes.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = rowEnTete.createCell(i);
                cell.setCellValue(entetes[i]);
                cell.setCellStyle(styleEnTete);
            }

            int numLigne = 1;
            CellStyle styleNormal = creerStyleNormal(workbook);
            for (Transaction tx : transactions) {
                Row row = sheet.createRow(numLigne++);
                row.createCell(0).setCellValue(DateUtil.formaterDateHeure(tx.getEnregistreLe()));
                row.createCell(1).setCellValue(tx.getBlockchainTxId() != null
                    ? tx.getBlockchainTxId() : "—");
                row.createCell(2).setCellValue(tx.getType());
                row.createCell(3).setCellValue(tx.getProduitNom());
                row.createCell(4).setCellValue(tx.getProduitReference());
                row.createCell(5).setCellValue(tx.getQuantite());
                row.createCell(6).setCellValue(tx.getQuantiteAvant() != null
                    ? tx.getQuantiteAvant() : 0);
                row.createCell(7).setCellValue(tx.getQuantiteApres() != null
                    ? tx.getQuantiteApres() : 0);
                row.createCell(8).setCellValue(tx.getOperateurNom());
                row.createCell(9).setCellValue(tx.getEntrepotSourceNom() != null
                    ? tx.getEntrepotSourceNom() : "—");
                row.createCell(10).setCellValue(tx.getEntrepotDestinationNom() != null
                    ? tx.getEntrepotDestinationNom() : "—");
                row.createCell(11).setCellValue(tx.getStatut());
            }

            for (int i = 0; i < 12; i++) sheet.autoSizeColumn(i);
            workbook.write(fos);
        }

        LOG.info("Rapport Excel mouvements généré.");
    }

    // ================================================================
    // Méthodes utilitaires PDF (iText 7)
    // ================================================================

    private void ajouterEnteteRapport(Document doc, String titre, String sousTitre)
            throws IOException {
        doc.add(new Paragraph(titre)
            .setFontColor(COULEUR_PRINCIPALE)
            .setFontSize(18)
            .setBold()
            .setMarginBottom(5));

        doc.add(new Paragraph("Système de Gestion d'Inventaire — Blockchain")
            .setFontColor(new DeviceRgb(100, 100, 100))
            .setFontSize(10)
            .setMarginBottom(3));

        doc.add(new Paragraph(sousTitre)
            .setFontColor(new DeviceRgb(80, 80, 80))
            .setFontSize(9)
            .setMarginBottom(15));

        // Ligne de séparation
        Table separateur = new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth();
        separateur.addCell(new Cell()
            .setHeight(2).setBackgroundColor(COULEUR_PRINCIPALE).setBorder(Border.NO_BORDER));
        doc.add(separateur);
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private Table creerTableau(float[] largeurs) {
        return new Table(UnitValue.createPercentArray(largeurs)).useAllAvailableWidth();
    }

    private void ajouterEnTeteTableau(Table table, String... colonnes) {
        for (String colonne : colonnes) {
            table.addHeaderCell(new Cell()
                .add(new Paragraph(colonne)
                    .setFontColor(BLANC)
                    .setFontSize(8)
                    .setBold())
                .setBackgroundColor(COULEUR_EN_TETE)
                .setBorder(Border.NO_BORDER)
                .setPadding(5));
        }
    }

    private void ajouterCellule(Table table, String texte,
                                  DeviceRgb fond, TextAlignment alignement) {
        table.addCell(new Cell()
            .add(new Paragraph(texte != null ? texte : "—")
                .setFontSize(8)
                .setTextAlignment(alignement))
            .setBackgroundColor(fond)
            .setBorder(Border.NO_BORDER)
            .setPadding(4));
    }

    private void ajouterPiedPage(Document doc) {
        doc.add(new Paragraph("\n\nDocument généré automatiquement par le Système d'Inventaire Blockchain. "
            + "© " + LocalDate.now().getYear())
            .setFontColor(new DeviceRgb(150, 150, 150))
            .setFontSize(7)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(20));
    }

    // ================================================================
    // Méthodes utilitaires Excel (Apache POI)
    // ================================================================

    private CellStyle creerStyleEnTete(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle creerStyleNormal(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle creerStyleAlterne(Workbook wb) {
        CellStyle style = creerStyleNormal(wb);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle creerStyleStatut(Workbook wb, byte[] rgb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
