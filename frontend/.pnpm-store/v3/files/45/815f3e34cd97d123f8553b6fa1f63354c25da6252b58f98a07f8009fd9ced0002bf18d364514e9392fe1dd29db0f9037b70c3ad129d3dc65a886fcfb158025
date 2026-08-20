import { toConsumableArray as _toConsumableArray } from "../_virtual/_rollupPluginBabelHelpers.js";
function callHandler(obj, handler, next) {
  for (var _len = arguments.length, args = new Array(_len > 3 ? _len - 3 : 0), _key = 3; _key < _len; _key++) {
    args[_key - 3] = arguments[_key];
  }
  var ret = handler.call.apply(handler, [obj].concat(args));
  if (!next || typeof next !== "function") {
    return;
  }
  if (ret && ret.then) {
    ret.then(function() {
      for (var _len2 = arguments.length, args2 = new Array(_len2), _key2 = 0; _key2 < _len2; _key2++) {
        args2[_key2] = arguments[_key2];
      }
      next.call.apply(next, [obj].concat(args2));
    });
  } else {
    next.call.apply(next, [obj].concat(args));
  }
}
function hook(hookName, handler) {
  var preset = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {
    pre: null,
    next: null
  };
  if (!this.__hooks) {
    this.__hooks = {};
  }
  !this.__hooks[hookName] && (this.__hooks[hookName] = null);
  return function() {
    var _arguments = arguments, _this = this;
    if (preset.pre) {
      try {
        var _preset$pre;
        (_preset$pre = preset.pre).call.apply(_preset$pre, [this].concat(Array.prototype.slice.call(arguments)));
      } catch (e) {
        e.message = "[pluginName: ".concat(this.pluginName, ":").concat(hookName, ":pre error] >> ").concat(e.message);
        throw e;
      }
    }
    if (this.__hooks && this.__hooks[hookName]) {
      try {
        var preRet = runHooks.apply(void 0, [this, hookName, handler].concat(Array.prototype.slice.call(arguments)));
        if (preRet) {
          if (preRet.then) {
            preRet.then(function(isContinue) {
              if (isContinue !== false) {
                callHandler.apply(void 0, [_this, handler, preset.next].concat(_toConsumableArray(_arguments)));
              }
            }).catch(function(e) {
              throw e;
            });
          } else {
            callHandler.apply(void 0, [this, handler, preset.next].concat(Array.prototype.slice.call(arguments)));
          }
        } else if (preRet === void 0) {
          callHandler.apply(void 0, [this, handler, preset.next].concat(Array.prototype.slice.call(arguments)));
        }
      } catch (e) {
        e.message = "[pluginName: ".concat(this.pluginName, ":").concat(hookName, "] >> ").concat(e.message);
        throw e;
      }
    } else {
      callHandler.apply(void 0, [this, handler, preset.next].concat(Array.prototype.slice.call(arguments)));
    }
  }.bind(this);
}
function findHookIndex(hookName, handler) {
  var __hooks = this.__hooks;
  if (!__hooks || !Array.isArray(__hooks[hookName])) {
    return -1;
  }
  var hookHandlers = __hooks[hookName];
  for (var i = 0; i < hookHandlers.length; i++) {
    if (hookHandlers[i] === handler) {
      return i;
    }
  }
  return -1;
}
function useHooks(hookName, handler) {
  var __hooks = this.__hooks;
  if (!__hooks) {
    return;
  }
  if (!__hooks.hasOwnProperty(hookName)) {
    console.warn("has no supported hook which name [".concat(hookName, "]"));
    return false;
  }
  if (!Array.isArray(__hooks[hookName])) {
    __hooks[hookName] = [];
  }
  if (findHookIndex.call(this, hookName, handler) === -1) {
    __hooks[hookName].push(handler);
  }
  return true;
}
function removeHooks(hookName, handler) {
  var __hooks = this.__hooks;
  if (!__hooks) {
    return;
  }
  if (Array.isArray(__hooks[hookName])) {
    var hooks = __hooks[hookName];
    var index = findHookIndex.call(this, hookName, handler);
    if (index !== -1) {
      hooks.splice(index, 1);
    }
  } else if (__hooks[hookName]) {
    __hooks[hookName] = null;
  }
}
function usePluginHooks(pluginName) {
  if (!this.plugins || !this.plugins[pluginName.toLowerCase()]) {
    return;
  }
  var plugin = this.plugins[pluginName.toLowerCase()];
  for (var _len3 = arguments.length, args = new Array(_len3 > 1 ? _len3 - 1 : 0), _key3 = 1; _key3 < _len3; _key3++) {
    args[_key3 - 1] = arguments[_key3];
  }
  return plugin.useHooks && plugin.useHooks.apply(plugin, args);
}
function removePluginHooks(pluginName) {
  if (!this.plugins || !this.plugins[pluginName.toLowerCase()]) {
    return;
  }
  var plugin = this.plugins[pluginName.toLowerCase()];
  if (plugin) {
    for (var _len4 = arguments.length, args = new Array(_len4 > 1 ? _len4 - 1 : 0), _key4 = 1; _key4 < _len4; _key4++) {
      args[_key4 - 1] = arguments[_key4];
    }
    return plugin.removeHooks && plugin.removeHooks.apply(plugin, args);
  }
}
function hooksDescriptor(instance) {
  var presetHooks = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : [];
  instance.__hooks = {};
  presetHooks && presetHooks.map(function(item) {
    instance.__hooks[item] = null;
  });
  Object.defineProperty(instance, "hooks", {
    get: function get() {
      return instance.__hooks && Object.keys(instance.__hooks).map(function(key) {
        if (instance.__hooks[key]) {
          return key;
        }
      });
    }
  });
}
function delHooksDescriptor(instance) {
  instance.__hooks = null;
}
function runHooks(obj, hookName, handler) {
  for (var _len5 = arguments.length, args = new Array(_len5 > 3 ? _len5 - 3 : 0), _key5 = 3; _key5 < _len5; _key5++) {
    args[_key5 - 3] = arguments[_key5];
  }
  if (obj.__hooks && Array.isArray(obj.__hooks[hookName])) {
    var hooks = obj.__hooks[hookName];
    var index = -1;
    var runHooksRecursive = function runHooksRecursive2(obj2, hookName2, handler2) {
      for (var _len6 = arguments.length, args2 = new Array(_len6 > 3 ? _len6 - 3 : 0), _key6 = 3; _key6 < _len6; _key6++) {
        args2[_key6 - 3] = arguments[_key6];
      }
      index++;
      if (hooks.length === 0 || index === hooks.length) {
        return handler2.call.apply(handler2, [obj2, obj2].concat(args2));
      }
      var hook2 = hooks[index];
      var ret = hook2.call.apply(hook2, [obj2, obj2].concat(args2));
      if (ret && ret.then) {
        return ret.then(function(data) {
          return data === false ? null : runHooksRecursive2.apply(void 0, [obj2, hookName2, handler2].concat(args2));
        }).catch(function(e) {
          console.warn("[runHooks]".concat(hookName2, " reject"), e.message);
        });
      } else if (ret !== false) {
        return runHooksRecursive2.apply(void 0, [obj2, hookName2, handler2].concat(args2));
      } else if (ret === false) {
        return false;
      }
    };
    return runHooksRecursive.apply(void 0, [obj, hookName, handler].concat(args));
  } else {
    return handler.call.apply(handler, [obj, obj].concat(args));
  }
}
export { hooksDescriptor as default, delHooksDescriptor, hook, removeHooks, removePluginHooks, runHooks, useHooks, usePluginHooks };
