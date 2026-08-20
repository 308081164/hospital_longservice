import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass, objectSpread2 as _objectSpread2 } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { SHORTCUT } from "../../events.js";
import BasePlugin from "../../plugin/basePlugin.js";
import "delegate";
function preventDefault(e) {
  e.preventDefault();
  e.returnValue = false;
}
function isDisableTag(el) {
  var tagName = el.tagName;
  if (tagName === "INPUT" || tagName === "TEXTAREA" || el.isContentEditable) {
    return true;
  }
  return false;
}
var Keyboard = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(Keyboard2, _BasePlugin);
  var _super = _createSuper(Keyboard2);
  function Keyboard2() {
    var _this;
    _classCallCheck(this, Keyboard2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "onBodyKeyDown", function(event) {
      if (!_this.player) {
        return;
      }
      var e = event || window.event;
      var keyCode = e.keyCode;
      var _assertThisInitialize = _assertThisInitialized(_this), _keyState = _assertThisInitialize._keyState, player = _assertThisInitialize.player;
      var _this$config = _this.config, disable = _this$config.disable, disableBodyTrigger = _this$config.disableBodyTrigger, isIgnoreUserActive = _this$config.isIgnoreUserActive;
      if (disable || disableBodyTrigger || !(player.isUserActive || isIgnoreUserActive) || isDisableTag(e.target) || !_this.checkIsVisible() || e.metaKey || e.altKey || e.ctrlKey) {
        _keyState.isBodyKeyDown = false;
        return;
      }
      if (!event.repeat && !_keyState.isKeyDown) {
        if ((e.target === document.body || _this.config.isGlobalTrigger && !isDisableTag(e.target)) && _this.checkCode(keyCode, true)) {
          _keyState.isBodyKeyDown = true;
        }
        document.addEventListener("keyup", _this.onBodyKeyUp);
      }
      _keyState.isBodyKeyDown && _this.handleKeyDown(e);
    });
    _defineProperty(_assertThisInitialized(_this), "onBodyKeyUp", function(event) {
      if (!_this.player) {
        return;
      }
      document.removeEventListener("keyup", _this.onBodyKeyUp);
      _this.handleKeyUp(event);
    });
    _defineProperty(_assertThisInitialized(_this), "onKeydown", function(event) {
      if (!_this.player) {
        return;
      }
      var e = event || window.event;
      var _assertThisInitialize2 = _assertThisInitialized(_this), _keyState = _assertThisInitialize2._keyState;
      if (!e.repeat) {
        if (_this.config.disable || _this.config.disableRootTrigger || e.metaKey || e.altKey || e.ctrlKey) {
          return;
        }
        if (e && (e.keyCode === 37 || _this.checkCode(e.keyCode)) && (e.target === _this.player.root || e.target === _this.player.video || e.target === _this.player.controls.el)) {
          _keyState.isKeyDown = true;
        }
        _this.player.root.addEventListener("keyup", _this.onKeyup);
      }
      if (!_keyState.isKeyDown) {
        return;
      }
      _this.handleKeyDown(e);
    });
    _defineProperty(_assertThisInitialized(_this), "onKeyup", function(event) {
      if (!_this.player) {
        return;
      }
      _this.player.root.removeEventListener("keyup", _this.onKeyup);
      _this.handleKeyUp(event);
    });
    return _this;
  }
  _createClass(Keyboard2, [{
    key: "mergekeyCodeMap",
    value: function mergekeyCodeMap() {
      var _this2 = this;
      var extendkeyCodeMap = this.config.keyCodeMap;
      if (extendkeyCodeMap) {
        Object.keys(extendkeyCodeMap).map(function(key) {
          if (!_this2.keyCodeMap[key]) {
            _this2.keyCodeMap[key] = extendkeyCodeMap[key];
          } else {
            ["keyCode", "action", "disable", "pressAction", "disablePress", "isBodyTarget"].map(function(key1) {
              extendkeyCodeMap[key][key1] && (_this2.keyCodeMap[key][key1] = extendkeyCodeMap[key][key1]);
            });
          }
        });
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      this.config.disable = !this.playerConfig.keyShortcut;
      var seekStep = typeof this.config.seekStep === "function" ? this.config.seekStep(this.player) : this.config.seekStep;
      if (!(!seekStep || typeof seekStep !== "number")) {
        this.seekStep = seekStep;
      }
      this.keyCodeMap = {
        space: {
          keyCode: 32,
          action: "playPause",
          disable: false,
          disablePress: false,
          noBodyTarget: false
        },
        up: {
          keyCode: 38,
          action: "upVolume",
          disable: false,
          disablePress: false,
          noBodyTarget: true
        },
        down: {
          keyCode: 40,
          action: "downVolume",
          disable: false,
          disablePress: false,
          noBodyTarget: true
        },
        left: {
          keyCode: 37,
          action: "seekBack",
          disablePress: false,
          disable: false
        },
        right: {
          keyCode: 39,
          action: "seek",
          pressAction: "changePlaybackRate",
          disablePress: false,
          disable: false
        },
        esc: {
          keyCode: 27,
          action: "exitFullscreen",
          disablePress: true,
          disable: false
        }
      };
      this.mergekeyCodeMap();
      this._keyState = {
        isKeyDown: false,
        isBodyKeyDown: false,
        isPress: false,
        tt: 0,
        playbackRate: 0
      };
      this.player.root.addEventListener("keydown", this.onKeydown);
      document.addEventListener("keydown", this.onBodyKeyDown);
    }
  }, {
    key: "setConfig",
    value: function setConfig(newConfig) {
      var _this3 = this;
      Object.keys(newConfig).forEach(function(key) {
        _this3.config[key] = newConfig[key];
      });
    }
  }, {
    key: "checkIsVisible",
    value: function checkIsVisible() {
      if (!this.config.checkVisible) {
        return true;
      }
      var rec = this.player.root.getBoundingClientRect();
      var height = rec.height, top = rec.top, bottom = rec.bottom;
      var h = window.innerHeight;
      if (top < 0 && top < 0 - height * 0.9 || bottom > 0 && bottom - h > height * 0.9) {
        return false;
      }
      return true;
    }
  }, {
    key: "checkCode",
    value: function checkCode(code, isBodyTarget) {
      var _this4 = this;
      var flag = false;
      Object.keys(this.keyCodeMap).map(function(key) {
        if (_this4.keyCodeMap[key] && code === _this4.keyCodeMap[key].keyCode && !_this4.keyCodeMap[key].disable) {
          flag = !isBodyTarget || isBodyTarget && !_this4.keyCodeMap[key].noBodyTarget;
        }
      });
      return flag;
    }
  }, {
    key: "downVolume",
    value: function downVolume(event) {
      var player = this.player;
      if (player.volume <= 0) {
        return;
      }
      var val = parseFloat((player.volume - 0.1).toFixed(1));
      var props = {
        volume: {
          from: player.volume,
          to: val
        }
      };
      this.emitUserAction(event, "change_volume", {
        props
      });
      if (val >= 0) {
        player.volume = val;
      } else {
        player.volume = 0;
      }
    }
  }, {
    key: "upVolume",
    value: function upVolume(event) {
      var player = this.player;
      if (player.volume >= 1) {
        return;
      }
      var val = parseFloat((player.volume + 0.1).toFixed(1));
      var props = {
        volume: {
          from: player.volume,
          to: val
        }
      };
      this.emitUserAction(event, "change_volume", {
        props
      });
      if (val <= 1) {
        player.volume = val;
      } else {
        player.volume = 1;
      }
    }
  }, {
    key: "seek",
    value: function seek(event) {
      var _this$player = this.player, currentTime = _this$player.currentTime, offsetCurrentTime = _this$player.offsetCurrentTime, duration = _this$player.duration, offsetDuration = _this$player.offsetDuration, timeSegments = _this$player.timeSegments;
      var _time = offsetCurrentTime > -1 ? offsetCurrentTime : currentTime;
      var _duration = offsetDuration || duration;
      var _step = event.repeat && this.seekStep >= 4 ? parseInt(this.seekStep / 2, 10) : this.seekStep;
      if (_time + _step <= _duration) {
        _time = _time + _step;
      } else {
        _time = _duration;
      }
      var _seekTime = util.getCurrentTimeByOffset(_time, timeSegments);
      var props = {
        currentTime: {
          from: currentTime,
          to: _seekTime
        }
      };
      this.emitUserAction(event, "seek", {
        props
      });
      this.player.currentTime = _seekTime;
    }
  }, {
    key: "seekBack",
    value: function seekBack(event) {
      var _this$player2 = this.player, currentTime = _this$player2.currentTime, offsetCurrentTime = _this$player2.offsetCurrentTime, timeSegments = _this$player2.timeSegments;
      var _step = event.repeat ? parseInt(this.seekStep / 2, 10) : this.seekStep;
      var _time = offsetCurrentTime > -1 ? offsetCurrentTime : currentTime;
      var _seekTime = _time - _step;
      if (_seekTime < 0) {
        _seekTime = 0;
      }
      _seekTime = util.getCurrentTimeByOffset(_seekTime, timeSegments);
      var props = {
        currentTime: {
          from: currentTime,
          to: _seekTime
        }
      };
      this.emitUserAction(event, "seek", {
        props
      });
      this.player.currentTime = _seekTime;
    }
  }, {
    key: "changePlaybackRate",
    value: function changePlaybackRate(event) {
      var _keyState = this._keyState, config = this.config, player = this.player;
      if (_keyState.playbackRate === 0) {
        _keyState.playbackRate = player.playbackRate;
        player.playbackRate = config.playbackRate;
      }
    }
  }, {
    key: "playPause",
    value: function playPause(event) {
      var player = this.player;
      if (!player) {
        return;
      }
      this.emitUserAction(event, "switch_play_pause");
      if (player.paused) {
        player.play();
      } else {
        player.pause();
      }
    }
  }, {
    key: "exitFullscreen",
    value: function exitFullscreen(event) {
      var player = this.player;
      var fullscreen = player.fullscreen, cssfullscreen = player.cssfullscreen;
      if (fullscreen) {
        this.emitUserAction("keyup", "switch_fullscreen", {
          prop: "fullscreen",
          from: fullscreen,
          to: !fullscreen
        });
        player.exitFullscreen();
      }
      if (cssfullscreen) {
        this.emitUserAction("keyup", "switch_css_fullscreen", {
          prop: "cssfullscreen",
          from: cssfullscreen,
          to: !cssfullscreen
        });
        player.exitCssFullscreen();
      }
    }
  }, {
    key: "handleKeyDown",
    value: function handleKeyDown(e) {
      var _keyState = this._keyState;
      if (e.repeat) {
        _keyState.isPress = true;
        var _t = Date.now();
        if (_t - _keyState.tt < 200) {
          return;
        }
        _keyState.tt = _t;
      }
      this.handleKeyCode(e.keyCode, e, _keyState.isPress);
    }
  }, {
    key: "handleKeyUp",
    value: function handleKeyUp(e) {
      var _keyState = this._keyState;
      if (_keyState.playbackRate > 0) {
        this.player.playbackRate = _keyState.playbackRate;
        _keyState.playbackRate = 0;
      }
      _keyState.isKeyDown = false;
      _keyState.isPress = false;
      _keyState.tt = 0;
    }
  }, {
    key: "handleKeyCode",
    value: function handleKeyCode(curKeyCode, event, isPress) {
      var arr = Object.keys(this.keyCodeMap);
      for (var i = 0; i < arr.length; i++) {
        var _this$keyCodeMap$arr$ = this.keyCodeMap[arr[i]], action = _this$keyCodeMap$arr$.action, keyCode = _this$keyCodeMap$arr$.keyCode, disable = _this$keyCodeMap$arr$.disable, pressAction = _this$keyCodeMap$arr$.pressAction, disablePress = _this$keyCodeMap$arr$.disablePress;
        if (keyCode === curKeyCode) {
          if (!disable && !(isPress && disablePress)) {
            var _action = !isPress ? action : pressAction || action;
            if (typeof _action === "function") {
              action(event, this.player, isPress);
            } else if (typeof _action === "string") {
              if (typeof this[_action] === "function") {
                this[_action](event, this.player, isPress);
              }
            }
            this.emit(SHORTCUT, _objectSpread2({
              key: arr[i],
              target: event.target,
              isPress
            }, this.keyCodeMap[arr[i]]));
          }
          preventDefault(event);
          event.stopPropagation();
          break;
        }
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.player.root.removeEventListener("keydown", this.onKeydown);
      document.removeEventListener("keydown", this.onBodyKeyDown);
      this.player.root.removeEventListener("keyup", this.onKeyup);
      document.removeEventListener("keyup", this.onBodyKeyUp);
    }
  }, {
    key: "disable",
    value: function disable() {
      this.config.disable = true;
    }
  }, {
    key: "enable",
    value: function enable() {
      this.config.disable = false;
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "keyboard";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        seekStep: 10,
        checkVisible: false,
        disableBodyTrigger: false,
        disableRootTrigger: false,
        isGlobalTrigger: true,
        keyCodeMap: {},
        disable: false,
        playbackRate: 2,
        isIgnoreUserActive: true
      };
    }
  }]);
  return Keyboard2;
}(BasePlugin);
export { Keyboard as default };
