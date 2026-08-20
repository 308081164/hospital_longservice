import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import { ENTER_PLAYER, LEAVE_PLAYER } from "../../events.js";
import BasePlugin from "../../plugin/basePlugin.js";
import "delegate";
import { runHooks } from "../../plugin/hooksDescriptor.js";
var MOUSE_EVENTS = {
  mouseenter: "onMouseEnter",
  mouseleave: "onMouseLeave",
  mousemove: "onMouseMove"
};
var HOOKS = ["videoClick", "videoDbClick"];
var PCPlugin = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(PCPlugin2, _BasePlugin);
  var _super = _createSuper(PCPlugin2);
  function PCPlugin2() {
    var _this;
    _classCallCheck(this, PCPlugin2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "onMouseMove", function(e) {
      var _assertThisInitialize = _assertThisInitialized(_this), player = _assertThisInitialize.player, playerConfig = _assertThisInitialize.playerConfig;
      if (!player.isActive) {
        player.focus({
          autoHide: !playerConfig.closeDelayBlur
        });
        !playerConfig.closeFocusVideoFocus && player.media.focus();
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseEnter", function(e) {
      var _assertThisInitialize2 = _assertThisInitialized(_this), playerConfig = _assertThisInitialize2.playerConfig, player = _assertThisInitialize2.player;
      !playerConfig.closeFocusVideoFocus && player.media.focus();
      if (playerConfig.closeDelayBlur) {
        player.focus({
          autoHide: false
        });
      } else {
        player.focus();
      }
      _this.emit(ENTER_PLAYER);
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseLeave", function(e) {
      var _this$playerConfig = _this.playerConfig, closePlayerBlur = _this$playerConfig.closePlayerBlur, leavePlayerTime = _this$playerConfig.leavePlayerTime, closeDelayBlur = _this$playerConfig.closeDelayBlur;
      if (!closePlayerBlur && !closeDelayBlur) {
        if (leavePlayerTime) {
          _this.player.focus({
            autoHide: true,
            delay: leavePlayerTime
          });
        } else {
          _this.player.blur({
            ignorePaused: true
          });
        }
      }
      _this.emit(LEAVE_PLAYER);
    });
    _defineProperty(_assertThisInitialized(_this), "onVideoClick", function(e) {
      var _assertThisInitialize3 = _assertThisInitialized(_this), player = _assertThisInitialize3.player, playerConfig = _assertThisInitialize3.playerConfig;
      if (e.target && playerConfig.closeVideoClick) {
        return;
      }
      if (e.target === player.root || e.target === player.media || e.target === player.innerContainer || e.target === player.media.__canvas) {
        e.preventDefault();
        if (!playerConfig.closeVideoStopPropagation) {
          e.stopPropagation();
        }
        _this._clickCount++;
        if (_this.clickTimer) {
          clearTimeout(_this.clickTimer);
          _this.clickTimer = null;
        }
        _this.clickTimer = setTimeout(function() {
          if (!_this._clickCount) {
            return;
          }
          _this._clickCount--;
          runHooks(_assertThisInitialized(_this), HOOKS[0], function(plugin, data) {
            _this.switchPlayPause(data.e);
          }, {
            e,
            paused: player.paused
          });
          clearTimeout(_this.clickTimer);
          _this.clickTimer = null;
        }, 300);
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onVideoDblClick", function(e) {
      var _assertThisInitialize4 = _assertThisInitialized(_this), player = _assertThisInitialize4.player, playerConfig = _assertThisInitialize4.playerConfig;
      if (playerConfig.closeVideoDblclick || !e.target || e.target !== player.media && e.target !== player.media.__canvas) {
        return;
      }
      if (!playerConfig.closeVideoClick && _this._clickCount < 2) {
        _this._clickCount = 0;
        return;
      }
      _this._clickCount = 0;
      if (_this.clickTimer) {
        clearTimeout(_this.clickTimer);
        _this.clickTimer = null;
      }
      e.preventDefault();
      e.stopPropagation();
      runHooks(_assertThisInitialized(_this), HOOKS[1], function(plugin, data) {
        _this.emitUserAction(data.e, "switch_fullscreen", {
          props: "fullscreen",
          from: player.fullscreen,
          to: !player.fullscreen
        });
        player.fullscreen ? player.exitFullscreen() : player.getFullscreen();
      }, {
        e,
        fullscreen: player.fullscreen
      });
    });
    return _this;
  }
  _createClass(PCPlugin2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      this._clickCount = 0;
      HOOKS.map(function(item) {
        _this2.__hooks[item] = null;
      });
      var isMobileSimulateMode = this.playerConfig.isMobileSimulateMode;
      if (isMobileSimulateMode === "mobile" || sniffer.device === "mobile" && !sniffer.os.isIpad) {
        return;
      }
      this.initEvents();
    }
  }, {
    key: "initEvents",
    value: function initEvents() {
      var _this3 = this;
      var _this$player = this.player, media = _this$player.media, root = _this$player.root;
      var enableContextmenu = this.playerConfig.enableContextmenu;
      root && root.addEventListener("click", this.onVideoClick, false);
      root && root.addEventListener("dblclick", this.onVideoDblClick, false);
      Object.keys(MOUSE_EVENTS).map(function(item) {
        root.addEventListener(item, _this3[MOUSE_EVENTS[item]], false);
      });
      !enableContextmenu && media && media.addEventListener("contextmenu", this.onContextmenu, false);
    }
  }, {
    key: "switchPlayPause",
    value: function switchPlayPause(e) {
      var player = this.player;
      this.emitUserAction(e, "switch_play_pause", {
        props: "paused",
        from: player.paused,
        to: !player.paused
      });
      if (!player.ended) {
        player.paused ? player.play() : player.pause();
      } else {
        player.duration !== Infinity && player.duration > 0 && player.replay();
      }
    }
  }, {
    key: "onContextmenu",
    value: function onContextmenu(e) {
      e = e || window.event;
      if (e.preventDefault) {
        e.preventDefault();
      }
      if (e.stopPropagation) {
        e.stopPropagation();
      } else {
        e.returnValue = false;
        e.cancelBubble = true;
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      var _this4 = this;
      var _this$player2 = this.player, video = _this$player2.video, root = _this$player2.root;
      this.clickTimer && clearTimeout(this.clickTimer);
      root.removeEventListener("click", this.onVideoClick, false);
      root.removeEventListener("dblclick", this.onVideoDblClick, false);
      video.removeEventListener("contextmenu", this.onContextmenu, false);
      Object.keys(MOUSE_EVENTS).map(function(item) {
        root.removeEventListener(item, _this4[MOUSE_EVENTS[item]], false);
      });
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "pc";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {};
    }
  }]);
  return PCPlugin2;
}(BasePlugin);
export { PCPlugin as default };
