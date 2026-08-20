import { createClass as _createClass, classCallCheck as _classCallCheck } from "../_virtual/_rollupPluginBabelHelpers.js";
var INDEXDB = /* @__PURE__ */ function() {
  function INDEXDB2() {
    var mydb = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {
      name: "xgplayer",
      version: 1,
      db: null,
      ojstore: {
        name: "xg-m4a",
        keypath: "vid"
      }
    };
    _classCallCheck(this, INDEXDB2);
    this.indexedDB = window.indexedDB || window.webkitindexedDB;
    this.IDBKeyRange = window.IDBKeyRange || window.webkitIDBKeyRange;
    this.myDB = mydb;
  }
  _createClass(INDEXDB2, [{
    key: "openDB",
    value: function openDB(callback) {
      var _this = this;
      var self = this;
      var version = this.myDB.version || 1;
      var request = self.indexedDB.open(self.myDB.name, version);
      request.onerror = function(e) {
      };
      request.onsuccess = function(e) {
        _this.myDB.db = e.target.result;
        callback.call(self);
      };
      request.onupgradeneeded = function(e) {
        var db = e.target.result;
        e.target.transaction;
        if (!db.objectStoreNames.contains(self.myDB.ojstore.name)) {
          db.createObjectStore(self.myDB.ojstore.name, {
            keyPath: self.myDB.ojstore.keypath
          });
        }
      };
    }
  }, {
    key: "deletedb",
    value: function deletedb() {
      var self = this;
      self.indexedDB.deleteDatabase(this.myDB.name);
    }
  }, {
    key: "closeDB",
    value: function closeDB() {
      this.myDB.db.close();
    }
  }, {
    key: "addData",
    value: function addData(storename, data) {
      var store = this.myDB.db.transaction(storename, "readwrite").objectStore(storename);
      var request;
      for (var i = 0; i < data.length; i++) {
        request = store.add(data[i]);
        request.onerror = function() {
        };
        request.onsuccess = function() {
        };
      }
    }
  }, {
    key: "putData",
    value: function putData(storename, data) {
      var store = this.myDB.db.transaction(storename, "readwrite").objectStore(storename);
      var request;
      for (var i = 0; i < data.length; i++) {
        request = store.put(data[i]);
        request.onerror = function() {
        };
        request.onsuccess = function() {
        };
      }
    }
  }, {
    key: "getDataByKey",
    value: function getDataByKey(storename, key, callback) {
      var self = this;
      var store = this.myDB.db.transaction(storename, "readwrite").objectStore(storename);
      var request = store.get(key);
      request.onerror = function() {
        callback.call(self, null);
      };
      request.onsuccess = function(e) {
        var result = e.target.result;
        callback.call(self, result);
      };
    }
  }, {
    key: "deleteData",
    value: function deleteData(storename, key) {
      var store = this.myDB.db.transaction(storename, "readwrite").objectStore(storename);
      store.delete(key);
    }
  }, {
    key: "clearData",
    value: function clearData(storename) {
      var store = this.myDB.db.transaction(storename, "readwrite").objectStore(storename);
      store.clear();
    }
  }]);
  return INDEXDB2;
}();
export { INDEXDB as default };
