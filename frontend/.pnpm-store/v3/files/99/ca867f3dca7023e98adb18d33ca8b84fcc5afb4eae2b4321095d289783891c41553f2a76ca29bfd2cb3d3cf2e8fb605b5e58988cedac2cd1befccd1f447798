import { createClass as _createClass, classCallCheck as _classCallCheck, defineProperty as _defineProperty } from "../_virtual/_rollupPluginBabelHelpers.js";
import { throttle } from "../utils/util.js";
import { PLATER_ID } from "../constant.js";
var ResizeObserver = /* @__PURE__ */ function() {
  function ResizeObserver2() {
    var _this = this;
    _classCallCheck(this, ResizeObserver2);
    _defineProperty(this, "__trigger", function(entries) {
      var t = new Date().getTime();
      _this.timeStamp = t;
      for (var i = 0; i < entries.length; i++) {
        _this.__runHandler(entries[i].target);
      }
    });
    this.__handlers = [];
    this.timeStamp = 0;
    this.observer = null;
    if (!window.ResizeObserver) {
      return;
    }
    try {
      this.observer = new window.ResizeObserver(throttle(this.__trigger, 100, {
        trailing: true
      }));
      this.timeStamp = new Date().getTime();
    } catch (e) {
      console.error(e);
    }
  }
  _createClass(ResizeObserver2, [{
    key: "addObserver",
    value: function addObserver2(target, handler) {
      if (!this.observer) {
        return;
      }
      this.observer.observe(target);
      var _pid = target.getAttribute(PLATER_ID);
      var __handlers = this.__handlers;
      var index = -1;
      for (var i = 0; i < __handlers.length; i++) {
        if (__handlers[i] && target === __handlers[i].target) {
          index = i;
        }
      }
      if (index > -1) {
        this.__handlers[index].handler = handler;
      } else {
        this.__handlers.push({
          target,
          handler,
          playerId: _pid
        });
      }
    }
  }, {
    key: "unObserver",
    value: function unObserver2(target) {
      var i = -1;
      this.__handlers.map(function(item, index) {
        if (target === item.target) {
          i = index;
        }
      });
      try {
        var _this$observer;
        (_this$observer = this.observer) === null || _this$observer === void 0 ? void 0 : _this$observer.unobserve(target);
      } catch (e) {
      }
      i > -1 && this.__handlers.splice(i, 1);
    }
  }, {
    key: "destroyObserver",
    value: function destroyObserver() {
      var _this$observer2;
      (_this$observer2 = this.observer) === null || _this$observer2 === void 0 ? void 0 : _this$observer2.disconnect();
      this.observer = null;
      this.__handlers = null;
    }
  }, {
    key: "__runHandler",
    value: function __runHandler(target) {
      var __handlers = this.__handlers;
      for (var i = 0; i < __handlers.length; i++) {
        if (__handlers[i] && target === __handlers[i].target) {
          try {
            __handlers[i].handler(target);
          } catch (error) {
            console.error(error);
          }
          return true;
        }
      }
      return false;
    }
  }]);
  return ResizeObserver2;
}();
var resizeObserver = null;
function addObserver(target, handler) {
  if (!resizeObserver) {
    resizeObserver = new ResizeObserver();
  }
  resizeObserver.addObserver(target, handler);
  return resizeObserver;
}
function unObserver(target, handler) {
  var _resizeObserver;
  (_resizeObserver = resizeObserver) === null || _resizeObserver === void 0 ? void 0 : _resizeObserver.unObserver(target, handler);
}
export { addObserver, unObserver };
