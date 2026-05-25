// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

contract InventaireContract {
    
    struct Product {
        string productRef;
        string name;
        string description;
        uint256 price;
        uint256 quantity;
    }
    
    struct Transaction {
        string transactionId;
        string productReference;
        string transactionType; // "ENTREE" or "SORTIE"
        uint256 quantity;
        uint256 date;
        string user;
    }

    mapping(string => Product) public products;
    Transaction[] public transactions;
    
    event ProductAdded(string reference, string name);
    event TransactionRecorded(string transactionId, string productReference, string transactionType, uint256 quantity);

    function addProduct(string memory _reference, string memory _name, string memory _description, uint256 _price) public {
        products[_reference] = Product(_reference, _name, _description, _price, 0);
        emit ProductAdded(_reference, _name);
    }
    
    function recordTransaction(
        string memory _transactionId,
        string memory _productReference, 
        string memory _transactionType, 
        uint256 _quantity,
        string memory _user
    ) public {
        Product storage prod = products[_productReference];
        require(bytes(prod.productRef).length > 0, "Product does not exist");
        
        if (keccak256(abi.encodePacked(_transactionType)) == keccak256(abi.encodePacked("ENTREE"))) {
            prod.quantity += _quantity;
        } else if (keccak256(abi.encodePacked(_transactionType)) == keccak256(abi.encodePacked("SORTIE"))) {
            require(prod.quantity >= _quantity, "Insufficient stock");
            prod.quantity -= _quantity;
        } else {
            revert("Invalid transaction type");
        }
        
        transactions.push(Transaction({
            transactionId: _transactionId,
            productReference: _productReference,
            transactionType: _transactionType,
            quantity: _quantity,
            date: block.timestamp,
            user: _user
        }));
        
        emit TransactionRecorded(_transactionId, _productReference, _transactionType, _quantity);
    }
    
    function getProduct(string memory _reference) public view returns (string memory, string memory, string memory, uint256, uint256) {
        Product memory p = products[_reference];
        return (p.productRef, p.name, p.description, p.price, p.quantity);
    }
    
    function getTransactionsCount() public view returns (uint256) {
        return transactions.length;
    }
    
    function getTransaction(uint256 index) public view returns (string memory, string memory, string memory, uint256, uint256, string memory) {
        require(index < transactions.length, "Index out of bounds");
        Transaction memory t = transactions[index];
        return (t.transactionId, t.productReference, t.transactionType, t.quantity, t.date, t.user);
    }
}