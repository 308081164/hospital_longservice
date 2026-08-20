export default INDEXDB;
declare class INDEXDB {
    constructor(mydb?: {
        name: string;
        version: number;
        db: any;
        ojstore: {
            name: string;
            keypath: string;
        };
    });
    indexedDB: any;
    IDBKeyRange: any;
    myDB: {
        name: string;
        version: number;
        db: any;
        ojstore: {
            name: string;
            keypath: string;
        };
    };
    openDB(callback: any): void;
    deletedb(): void;
    closeDB(): void;
    addData(storename: any, data: any): void;
    putData(storename: any, data: any): void;
    getDataByKey(storename: any, key: any, callback: any): void;
    deleteData(storename: any, key: any): void;
    clearData(storename: any): void;
}
