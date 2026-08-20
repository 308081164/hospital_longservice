import { createClass as _createClass, classCallCheck as _classCallCheck, objectSpread2 as _objectSpread2 } from "../_virtual/_rollupPluginBabelHelpers.js";
import util from "../utils/util.js";
export { default as Util } from "../utils/util.js";
import XG_DEBUG from "../utils/debug.js";
export { default as XG_DEBUG } from "../utils/debug.js";
import hooksDescriptor, { hook, useHooks, removeHooks, delHooksDescriptor } from "./hooksDescriptor.js";
function showErrorMsg(pluginName, msg) {
  XG_DEBUG.logError("[".concat(pluginName, "] event or callback cant be undefined or null when call ").concat(msg));
}
var BasePlugin = /* @__PURE__ */ function() {
  function BasePlugin2(args) {
    _classCallCheck(this, BasePlugin2);
    if (util.checkIsFunction(this.beforeCreate)) {
      this.beforeCreate(args);
    }
    hooksDescriptor(this);
    this.__args = args;
    this.__events = {};
    this.__onceEvents = {};
    this.config = args.config || {};
    this.player = null;
    this.playerConfig = {};
    this.pluginName = "";
    this.__init(args);
  }
  _createClass(BasePlugin2, [{
    key: "beforeCreate",
    value: function beforeCreate(args) {
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
    }
  }, {
    key: "beforePlayerInit",
    value: function beforePlayerInit() {
    }
  }, {
    key: "onPluginsReady",
    value: function onPluginsReady() {
    }
  }, {
    key: "afterPlayerInit",
    value: function afterPlayerInit() {
    }
  }, {
    key: "destroy",
    value: function destroy() {
    }
  }, {
    key: "__init",
    value: function __init(args) {
      this.player = args.player;
      this.playerConfig = args.player && args.player.config;
      this.pluginName = args.pluginName ? args.pluginName.toLowerCase() : this.constructor.pluginName.toLowerCase();
      this.logger = args.player && args.player.logger;
    }
  }, {
    key: "updateLang",
    value: function updateLang(lang) {
      if (!lang) {
        lang = this.lang;
      }
    }
  }, {
    key: "lang",
    get: function get() {
      return this.player.lang;
    }
  }, {
    key: "i18n",
    get: function get() {
      return this.player.i18n;
    }
  }, {
    key: "i18nKeys",
    get: function get() {
      return this.player.i18nKeys;
    }
  }, {
    key: "domEventType",
    get: function get() {
      var _e = util.checkTouchSupport() ? "touch" : "mouse";
      if (this.playerConfig && (this.playerConfig.domEventType === "touch" || this.playerConfig.domEventType === "mouse")) {
        _e = this.playerConfig.domEventType;
      }
      return _e;
    }
  }, {
    key: "on",
    value: function on(event, callback) {
      var _this = this;
      if (!event || !callback || !this.player) {
        showErrorMsg(this.pluginName, "plugin.on(event, callback)");
        return;
      }
      if (typeof event === "string") {
        this.__events[event] = callback;
        this.player.on(event, callback);
      } else if (Array.isArray(event)) {
        event.forEach(function(item) {
          _this.__events[item] = callback;
          _this.player.on(item, callback);
        });
      }
    }
  }, {
    key: "once",
    value: function once(event, callback) {
      var _this2 = this;
      if (!event || !callback || !this.player) {
        showErrorMsg(this.pluginName, "plugin.once(event, callback)");
        return;
      }
      if (typeof event === "string") {
        this.__onceEvents[event] = callback;
        this.player.once(event, callback);
      } else if (Array.isArray(event)) {
        event.forEach(function(item) {
          _this2.__onceEvents[item] = callback;
          _this2.player.once(event, callback);
        });
      }
    }
  }, {
    key: "off",
    value: function off(event, callback) {
      var _this3 = this;
      if (!event || !callback || !this.player) {
        showErrorMsg(this.pluginName, "plugin.off(event, callback)");
        return;
      }
      if (typeof event === "string") {
        delete this.__events[event];
        this.player.off(event, callback);
      } else if (Array.isArray(event)) {
        event.forEach(function(item) {
          delete _this3.__events[event];
          _this3.player.off(item, callback);
        });
      }
    }
  }, {
    key: "offAll",
    value: function offAll() {
      var _this4 = this;
      ["__events", "__onceEvents"].forEach(function(key) {
        Object.keys(_this4[key]).forEach(function(item) {
          _this4[key][item] && _this4.off(item, _this4[key][item]);
          item && delete _this4[key][item];
        });
      });
      this.__events = {};
      this.__onceEvents = {};
    }
  }, {
    key: "emit",
    value: function emit(event) {
      var _this$player;
      if (!this.player) {
        return;
      }
      for (var _len = arguments.length, res = new Array(_len > 1 ? _len - 1 : 0), _key = 1; _key < _len; _key++) {
        res[_key - 1] = arguments[_key];
      }
      (_this$player = this.player).emit.apply(_this$player, [event].concat(res));
    }
  }, {
    key: "emitUserAction",
    value: function emitUserAction(event, action) {
      var params = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {};
      if (!this.player) {
        return;
      }
      var nParams = _objectSpread2(_objectSpread2({}, params), {}, {
        pluginName: this.pluginName
      });
      this.player.emitUserAction(event, action, nParams);
    }
  }, {
    key: "hook",
    value: function hook$1(hookName, handler) {
      return hook.call.apply(hook, [this].concat(Array.prototype.slice.call(arguments)));
    }
  }, {
    key: "useHooks",
    value: function useHooks$1(hookName, handler) {
      for (var _len2 = arguments.length, args = new Array(_len2 > 2 ? _len2 - 2 : 0), _key2 = 2; _key2 < _len2; _key2++) {
        args[_key2 - 2] = arguments[_key2];
      }
      return useHooks.call.apply(useHooks, [this].concat(Array.prototype.slice.call(arguments)));
    }
  }, {
    key: "removeHooks",
    value: function removeHooks$1(hookName, handler) {
      for (var _len3 = arguments.length, args = new Array(_len3 > 2 ? _len3 - 2 : 0), _key3 = 2; _key3 < _len3; _key3++) {
        args[_key3 - 2] = arguments[_key3];
      }
      return removeHooks.call.apply(removeHooks, [this].concat(Array.prototype.slice.call(arguments)));
    }
  }, {
    key: "registerPlugin",
    value: function registerPlugin(plugin) {
      var options = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
      var name = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : "";
      if (!this.player) {
        return;
      }
      name && (options.pluginName = name);
      return this.player.registerPlugin({
        plugin,
        options
      });
    }
  }, {
    key: "getPlugin",
    value: function getPlugin(name) {
      return this.player ? this.player.getPlugin(name) : null;
    }
  }, {
    key: "__destroy",
    value: function __destroy() {
      var _this5 = this;
      var player = this.player;
      var pluginName = this.pluginName;
      this.offAll();
      util.clearAllTimers(this);
      if (util.checkIsFunction(this.destroy)) {
        this.destroy();
      }
      ["player", "playerConfig", "pluginName", "logger", "__args", "__hooks"].map(function(item) {
        _this5[item] = null;
      });
      player.unRegisterPlugin(pluginName);
      delHooksDescriptor(this);
    }
  }], [{
    key: "defineGetterOrSetter",
    value: function defineGetterOrSetter(Obj, map) {
      for (var key in map) {
        if (Object.prototype.hasOwnProperty.call(map, key)) {
          Object.defineProperty(Obj, key, map[key]);
        }
      }
    }
  }, {
    key: "defineMethod",
    value: function defineMethod(Obj, map) {
      for (var key in map) {
        if (Object.prototype.hasOwnProperty.call(map, key) && typeof map[key] === "function") {
          Object.defineProperty(Obj, key, {
            configurable: true,
            value: map[key]
          });
        }
      }
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {};
    }
  }, {
    key: "pluginName",
    get: function get() {
      return "pluginName";
    }
  }]);
  return BasePlugin2;
}();
export { BasePlugin as default };
