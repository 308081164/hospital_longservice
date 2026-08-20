import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck } from "./_virtual/_rollupPluginBabelHelpers.js";
import { EventEmitter } from "eventemitter3";
var store = {};
var instance = null;
var InstManager = /* @__PURE__ */ function(_EventEmitter) {
  _inherits(InstManager2, _EventEmitter);
  var _super = _createSuper(InstManager2);
  function InstManager2() {
    _classCallCheck(this, InstManager2);
    return _super.apply(this, arguments);
  }
  _createClass(InstManager2, [{
    key: "add",
    value: function add(player) {
      if (!player) {
        return;
      }
      store[player.playerId] = player;
      if (Object.keys(store).length === 1) {
        this.setActive(player.playerId, true);
      }
    }
  }, {
    key: "remove",
    value: function remove(player) {
      if (!player) {
        return;
      }
      player.isUserActive;
      delete store[player.playerId];
    }
  }, {
    key: "_iterate",
    value: function _iterate(fn) {
      var endEarly = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
      for (var key in store) {
        if (Object.prototype.hasOwnProperty.call(store, key)) {
          var player = store[key];
          if (endEarly) {
            if (fn(player)) {
              break;
            }
          } else {
            fn(player);
          }
        }
      }
    }
  }, {
    key: "forEach",
    value: function forEach(fn) {
      this._iterate(fn);
    }
  }, {
    key: "find",
    value: function find(fn) {
      var result = null;
      this._iterate(function(player) {
        var flag = fn(player);
        if (flag) {
          result = player;
        }
        return flag;
      }, true);
      return result;
    }
  }, {
    key: "findAll",
    value: function findAll(fn) {
      var results = [];
      this._iterate(function(player) {
        if (fn(player)) {
          results.push(player);
        }
      });
      return results;
    }
  }, {
    key: "setActive",
    value: function setActive(playerId) {
      var isActive = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
      if (!store[playerId]) {
        return;
      }
      if (isActive) {
        this.forEach(function(inst) {
          if (playerId === inst.playerId) {
            inst.isUserActive = true;
            inst.isInstNext = false;
          } else {
            inst.isUserActive = false;
          }
        });
      } else {
        store[playerId].isUserActive = isActive;
      }
      return playerId;
    }
  }, {
    key: "getActiveId",
    value: function getActiveId() {
      var keys = Object.keys(store);
      for (var i = 0; i < keys.length; i++) {
        var c = store[keys[i]];
        if (c && c.isUserActive) {
          return keys[i];
        }
      }
      return null;
    }
  }, {
    key: "setNext",
    value: function setNext(playerId) {
      var isNext = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
      if (!store[playerId]) {
        return;
      }
      if (isNext) {
        this.forEach(function(inst) {
          if (playerId === inst.playerId) {
            inst.isUserActive = false;
            inst.isInstNext = true;
          } else {
            inst.isInstNext = false;
          }
        });
      } else {
        store[playerId].isInstNext = isNext;
      }
      return playerId;
    }
  }], [{
    key: "getInstance",
    value: function getInstance() {
      instance || (instance = new InstManager2());
      return instance;
    }
  }]);
  return InstManager2;
}(EventEmitter);
function checkPlayerRoot(root) {
  var keys = Object.keys(store);
  for (var i = 0; i < keys.length; i++) {
    var p = store[keys[i]];
    if (p.root === root) {
      return p;
    }
  }
  return null;
}
export { InstManager, checkPlayerRoot };
