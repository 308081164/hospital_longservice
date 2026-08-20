import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import { DURATION_CHANGE, CANPLAY, ENDED } from "../../events.js";
import "../../utils/debug.js";
import { runHooks } from "../../plugin/hooksDescriptor.js";
import Plugin from "../../plugin/plugin.js";
import { STATES } from "../../state.js";
import Touche from "./touch.js";
import SeekTipIcon from "../assets/seekicon.js";
var ACTIONS = {
  AUTO: "auto",
  SEEKING: "seeking",
  PLAYBACK: "playbackrate",
  LIGHT: ""
};
var HOOKS = ["videoClick", "videoDbClick"];
var MobilePlugin = /* @__PURE__ */ function(_Plugin) {
  _inherits(MobilePlugin2, _Plugin);
  var _super = _createSuper(MobilePlugin2);
  function MobilePlugin2(options) {
    var _this;
    _classCallCheck(this, MobilePlugin2);
    _this = _super.call(this, options);
    _defineProperty(_assertThisInitialized(_this), "onTouchStart", function(e) {
      var _assertThisInitialize = _assertThisInitialized(_this), player = _assertThisInitialize.player, config = _assertThisInitialize.config, pos = _assertThisInitialize.pos, playerConfig = _assertThisInitialize.playerConfig;
      var touche = _this.getTouche(e);
      if (touche && !config.disableGesture && _this.duration > 0 && !player.ended) {
        pos.isStart = true;
        _this.timer && clearTimeout(_this.timer);
        util.checkIsFunction(playerConfig.disableSwipeHandler) && playerConfig.disableSwipeHandler();
        _this.find(".xg-dur").innerHTML = util.format(_this.duration);
        var rect = _this.root.getBoundingClientRect();
        if (player.rotateDeg === 90) {
          pos.top = rect.left;
          pos.left = rect.top;
          pos.width = rect.height;
          pos.height = rect.width;
        } else {
          pos.top = rect.top;
          pos.left = rect.left;
          pos.width = rect.width;
          pos.height = rect.height;
        }
        var _x = parseInt(touche.pageX - pos.left, 10);
        var _y = parseInt(touche.pageY - pos.top, 10);
        pos.x = player.rotateDeg === 90 ? _y : _x;
        pos.y = player.rotateDeg === 90 ? _x : _y;
        pos.scopeL = config.scopeL * pos.width;
        pos.scopeR = (1 - config.scopeR) * pos.width;
        pos.scopeM1 = pos.width * (1 - config.scopeM) / 2;
        pos.scopeM2 = pos.width - pos.scopeM1;
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onTouchMove", function(e) {
      var touche = _this.getTouche(e);
      var _assertThisInitialize2 = _assertThisInitialized(_this), pos = _assertThisInitialize2.pos, config = _assertThisInitialize2.config, player = _assertThisInitialize2.player;
      if (!touche || config.disableGesture || !_this.duration || !pos.isStart) {
        return;
      }
      var miniMoveStep = config.miniMoveStep, hideControlsActive = config.hideControlsActive;
      var _x = parseInt(touche.pageX - pos.left, 10);
      var _y = parseInt(touche.pageY - pos.top, 10);
      var x = player.rotateDeg === 90 ? _y : _x;
      var y = player.rotateDeg === 90 ? _x : _y;
      if (Math.abs(x - pos.x) > miniMoveStep || Math.abs(y - pos.y) > miniMoveStep) {
        var diffx = x - pos.x;
        var diffy = y - pos.y;
        var scope = pos.scope;
        if (scope === -1) {
          scope = _this.checkScope(x, y, diffx, diffy, pos);
          if (scope === 0) {
            !hideControlsActive ? player.focus({
              autoHide: false
            }) : player.blur();
            !pos.time && (pos.time = parseInt(player.currentTime * 1e3, 10) + _this.timeOffset * 1e3);
          }
          pos.scope = scope;
        }
        if (scope === -1 || scope > 0 && !config.gestureY || scope === 0 && !config.gestureX) {
          return;
        }
        _this.executeMove(diffx, diffy, scope, pos.width, pos.height);
        pos.x = x;
        pos.y = y;
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onTouchEnd", function(e) {
      var _assertThisInitialize3 = _assertThisInitialized(_this), player = _assertThisInitialize3.player, pos = _assertThisInitialize3.pos, playerConfig = _assertThisInitialize3.playerConfig;
      setTimeout(function() {
        player.getPlugin("progress") && player.getPlugin("progress").resetSeekState();
      }, 10);
      if (!pos.isStart) {
        return;
      }
      if (pos.scope > -1) {
        e.cancelable && e.preventDefault();
      }
      var _this$config = _this.config, disableGesture = _this$config.disableGesture, gestureX = _this$config.gestureX;
      if (!disableGesture && gestureX) {
        _this.endLastMove(pos.scope);
      } else {
        pos.time = 0;
      }
      pos.scope = -1;
      _this.resetPos();
      util.checkIsFunction(playerConfig.enableSwipeHandler) && playerConfig.enableSwipeHandler();
      _this.changeAction(ACTIONS.AUTO);
    });
    _defineProperty(_assertThisInitialized(_this), "onRootTouchMove", function(e) {
      if (_this.config.disableGesture || !_this.config.gestureX) {
        return;
      }
      if (_this.checkIsRootTarget(e)) {
        e.stopPropagation();
        if (!_this.pos.isStart) {
          _this.onTouchStart(e);
        } else {
          _this.onTouchMove(e);
        }
      }
    });
    _defineProperty(_assertThisInitialized(_this), "onRootTouchEnd", function(e) {
      if (_this.pos.scope > -1) {
        _this.onTouchEnd(e);
      }
    });
    _this.pos = {
      isStart: false,
      x: 0,
      y: 0,
      time: 0,
      volume: 0,
      rate: 1,
      light: 0,
      width: 0,
      height: 0,
      scopeL: 0,
      scopeR: 0,
      scopeM1: 0,
      scopeM2: 0,
      scope: -1
    };
    _this.timer = null;
    return _this;
  }
  _createClass(MobilePlugin2, [{
    key: "duration",
    get: function get() {
      return this.playerConfig.customDuration || this.player.duration;
    }
  }, {
    key: "timeOffset",
    get: function get() {
      return this.playerConfig.timeOffset || 0;
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        seekTipIcon: {
          icon: SeekTipIcon,
          class: "xg-seek-pre"
        }
      };
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      HOOKS.map(function(item) {
        _this2.__hooks[item] = null;
      });
      var playerConfig = this.playerConfig, config = this.config, player = this.player;
      if (playerConfig.closeVideoDblclick === true) {
        config.closedbClick = true;
      }
      this.resetPos();
      if (!util.isUndefined(playerConfig.disableGesture)) {
        config.disableGesture = !!playerConfig.disableGesture;
      }
      this.appendChild(".xg-seek-icon", this.icons.seekTipIcon);
      this.xgMask = util.createDom("xg-mask", "", {}, "xgmask");
      player.root.appendChild(this.xgMask);
      this.initCustomStyle();
      this.registerThumbnail();
      var eventType = this.domEventType === "mouse" ? "mouse" : "touch";
      this.touch = new Touche(this.root, {
        eventType,
        needPreventDefault: !this.config.disableGesture
      });
      this.root.addEventListener("contextmenu", function(e) {
        e.preventDefault();
      });
      player.root.addEventListener("touchmove", this.onRootTouchMove, true);
      player.root.addEventListener("touchend", this.onRootTouchEnd, true);
      player.root.addEventListener("touchcancel", this.onRootTouchEnd, true);
      var controls = this.player.controls;
      if (controls && controls.center) {
        controls.center.addEventListener("touchmove", this.onRootTouchMove, true);
        controls.center.addEventListener("touchend", this.onRootTouchEnd, true);
        controls.center.addEventListener("touchcancel", this.onRootTouchEnd, true);
      }
      this.on(DURATION_CHANGE, function() {
        var player2 = _this2.player, config2 = _this2.config;
        if (player2.duration > 0 && player2.duration * 1e3 < config2.moveDuration) {
          config2.moveDuration = player2.duration * 1e3;
        }
      });
      this.on([CANPLAY, ENDED], function() {
        var _this2$pos = _this2.pos, time = _this2$pos.time, isStart = _this2$pos.isStart;
        if (!isStart && time > 0) {
          _this2.pos.time = 0;
        }
      });
      var eventsMap = {
        touchstart: "onTouchStart",
        touchmove: "onTouchMove",
        touchend: "onTouchEnd",
        press: "onPress",
        pressend: "onPressEnd",
        click: "onClick",
        doubleclick: "onDbClick"
      };
      Object.keys(eventsMap).map(function(key) {
        _this2.touch.on(key, function(e) {
          _this2[eventsMap[key]](e);
        });
      });
      if (!config.disableActive) {
        var progressPlugin = player.plugins.progress;
        if (progressPlugin) {
          progressPlugin.addCallBack("dragmove", function(data) {
            _this2.activeSeekNote(data.currentTime, data.forward);
          });
          ["dragend", "click"].forEach(function(key) {
            progressPlugin.addCallBack(key, function() {
              _this2.changeAction(ACTIONS.AUTO);
            });
          });
        }
      }
    }
  }, {
    key: "registerThumbnail",
    value: function registerThumbnail() {
      var player = this.player;
      var thumbnail = player.plugins.thumbnail;
      if (thumbnail && thumbnail.usable) {
        this.thumbnail = thumbnail.createThumbnail(null, "mobile-thumbnail");
        var timePreview = this.find(".time-preview");
        timePreview.insertBefore(this.thumbnail, timePreview.children[0]);
      }
    }
  }, {
    key: "initCustomStyle",
    value: function initCustomStyle() {
      var _ref = this.playerConfig || {}, commonStyle = _ref.commonStyle;
      var playedColor = commonStyle.playedColor, progressColor = commonStyle.progressColor, timePreviewStyle = commonStyle.timePreviewStyle, curTimeColor = commonStyle.curTimeColor, durationColor = commonStyle.durationColor;
      if (playedColor) {
        this.find(".xg-curbar").style.backgroundColor = playedColor;
      }
      if (progressColor) {
        this.find(".xg-bar").style.backgroundColor = progressColor;
      }
      if (timePreviewStyle) {
        var previewDom = this.find(".time-preview");
        Object.keys(timePreviewStyle).forEach(function(key) {
          previewDom.style[key] = timePreviewStyle[key];
        });
      }
      var curColor = curTimeColor || playedColor;
      var durColor = durationColor;
      curColor && (this.find(".xg-cur").style.color = curColor);
      durColor && (this.find(".xg-dur").style.color = durColor);
      this.config.disableTimeProgress && util.addClass(this.find(".xg-timebar"), "hide");
    }
  }, {
    key: "resetPos",
    value: function resetPos() {
      var _this3 = this;
      if (this.pos) {
        this.pos.isStart = false;
        this.pos.scope = -1;
        ["x", "y", "width", "height", "scopeL", "scopeR", "scopeM1", "scopeM2"].map(function(item) {
          _this3.pos[item] = 0;
        });
      } else {
        this.pos = {
          isStart: false,
          x: 0,
          y: 0,
          volume: 0,
          rate: 1,
          light: 0,
          width: 0,
          height: 0,
          scopeL: 0,
          scopeR: 0,
          scopeM1: 0,
          scopeM2: 0,
          scope: -1,
          time: 0
        };
      }
    }
  }, {
    key: "changeAction",
    value: function changeAction(action) {
      var player = this.player, root = this.root;
      root.setAttribute("data-xg-action", action);
      var startPlugin = player.plugins.start;
      startPlugin && startPlugin.recover();
    }
  }, {
    key: "getTouche",
    value: function getTouche(e) {
      var rotateDeg = this.player.rotateDeg;
      var touche = e.touches && e.touches.length > 0 ? e.touches[e.touches.length - 1] : e;
      return rotateDeg === 0 ? {
        pageX: touche.pageX,
        pageY: touche.pageY
      } : {
        pageX: touche.pageX,
        pageY: touche.pageY
      };
    }
  }, {
    key: "checkScope",
    value: function checkScope(x, y, diffx, diffy, pos) {
      var width = pos.width;
      var scope = -1;
      if (x < 0 || x > width) {
        return scope;
      }
      var mold = diffy === 0 ? Math.abs(diffx) : Math.abs(diffx / diffy);
      if (Math.abs(diffx) > 0 && mold >= 1.73 && x > pos.scopeM1 && x < pos.scopeM2) {
        scope = 0;
      } else if (Math.abs(diffx) === 0 || mold <= 0.57) {
        scope = x < pos.scopeL ? 1 : x > pos.scopeR ? 2 : 3;
      }
      return scope;
    }
  }, {
    key: "executeMove",
    value: function executeMove(diffx, diffy, scope, width, height) {
      switch (scope) {
        case 0:
          this.updateTime(diffx / width * this.config.scopeM);
          break;
        case 1:
          this.updateBrightness(diffy / height);
          break;
        case 2:
          if (!sniffer.os.isIos) {
            this.updateVolume(diffy / height);
          }
          break;
      }
    }
  }, {
    key: "endLastMove",
    value: function endLastMove(lastScope) {
      var _this4 = this;
      var pos = this.pos, player = this.player, config = this.config;
      var time = (pos.time - this.timeOffset) / 1e3;
      switch (lastScope) {
        case 0:
          player.seek(Number(time).toFixed(1));
          config.hideControlsEnd ? player.blur() : player.focus();
          this.timer = setTimeout(function() {
            _this4.pos.time = 0;
          }, 500);
          break;
      }
      this.changeAction(ACTIONS.AUTO);
    }
  }, {
    key: "checkIsRootTarget",
    value: function checkIsRootTarget(e) {
      var plugins = this.player.plugins || {};
      if (plugins.progress && plugins.progress.root.contains(e.target)) {
        return false;
      }
      return plugins.start && plugins.start.root.contains(e.target) || plugins.controls && plugins.controls.root.contains(e.target);
    }
  }, {
    key: "sendUseAction",
    value: function sendUseAction(event) {
      var paused = this.player.paused;
      this.emitUserAction(event, "switch_play_pause", {
        prop: "paused",
        from: paused,
        to: !paused
      });
    }
  }, {
    key: "clickHandler",
    value: function clickHandler(e) {
      var player = this.player, config = this.config, playerConfig = this.playerConfig;
      if (player.state < STATES.RUNNING) {
        if (!playerConfig.closeVideoClick) {
          this.sendUseAction(util.createEvent("click"));
          player.play();
        }
        return;
      }
      if (!config.closedbClick || playerConfig.closeVideoClick) {
        player.isActive ? player.blur() : player.focus();
      } else if (!playerConfig.closeVideoClick) {
        if (player.isActive || config.focusVideoClick) {
          this.sendUseAction(util.createEvent("click"));
          this.switchPlayPause();
        }
        player.focus();
      }
    }
  }, {
    key: "dbClickHandler",
    value: function dbClickHandler(e) {
      var config = this.config, player = this.player;
      if (!config.closedbClick && player.state >= STATES.RUNNING) {
        this.sendUseAction(util.createEvent("dblclick"));
        this.switchPlayPause();
      }
    }
  }, {
    key: "onClick",
    value: function onClick(e) {
      var _this5 = this;
      var player = this.player;
      runHooks(this, HOOKS[0], function(plugin, data) {
        _this5.clickHandler(data.e);
      }, {
        e,
        paused: player.paused
      });
    }
  }, {
    key: "onDbClick",
    value: function onDbClick(e) {
      var _this6 = this;
      var player = this.player;
      runHooks(this, HOOKS[1], function(plugin, data) {
        _this6.dbClickHandler(data.e);
      }, {
        e,
        paused: player.paused
      });
    }
  }, {
    key: "onPress",
    value: function onPress(e) {
      var pos = this.pos, config = this.config, player = this.player;
      if (config.disablePress) {
        return;
      }
      pos.rate = this.player.playbackRate;
      this.emitUserAction("press", "change_rate", {
        prop: "playbackRate",
        from: player.playbackRate,
        to: config.pressRate
      });
      player.playbackRate = config.pressRate;
      this.changeAction(ACTIONS.PLAYBACK);
    }
  }, {
    key: "onPressEnd",
    value: function onPressEnd(e) {
      var pos = this.pos, config = this.config, player = this.player;
      if (config.disablePress) {
        return;
      }
      this.emitUserAction("pressend", "change_rate", {
        prop: "playbackRate",
        from: player.playbackRate,
        to: pos.rate
      });
      player.playbackRate = pos.rate;
      pos.rate = 1;
      this.changeAction(ACTIONS.AUTO);
    }
  }, {
    key: "updateTime",
    value: function updateTime(percent) {
      var player = this.player, config = this.config;
      var duration = this.player.duration;
      percent = Number(percent.toFixed(4));
      var time = parseInt(percent * config.moveDuration, 10) + this.timeOffset;
      time += this.pos.time;
      time = time < 0 ? 0 : time > duration * 1e3 ? duration * 1e3 - 200 : time;
      player.getPlugin("time") && player.getPlugin("time").updateTime(time / 1e3);
      player.getPlugin("progress") && player.getPlugin("progress").updatePercent(time / 1e3 / this.duration, true);
      this.activeSeekNote(time / 1e3, percent > 0);
      if (config.isTouchingSeek) {
        player.seek(Number((time - this.timeOffset) / 1e3).toFixed(1));
      }
      this.pos.time = time;
    }
  }, {
    key: "updateVolume",
    value: function updateVolume(percent) {
      if (this.player.rotateDeg) {
        percent = -percent;
      }
      var player = this.player, pos = this.pos;
      percent = parseInt(percent * 100, 10);
      pos.volume += percent;
      if (Math.abs(pos.volume) < 10) {
        return;
      }
      var volume = parseInt(player.volume * 10, 10) - parseInt(pos.volume / 10, 10);
      volume = volume > 10 ? 10 : volume < 1 ? 0 : volume;
      player.volume = volume / 10;
      pos.volume = 0;
    }
  }, {
    key: "updateBrightness",
    value: function updateBrightness(percent) {
      var pos = this.pos, config = this.config, xgMask = this.xgMask;
      if (!config.darkness) {
        return;
      }
      if (this.player.rotateDeg) {
        percent = -percent;
      }
      var light = pos.light + 0.8 * percent;
      light = light > config.maxDarkness ? config.maxDarkness : light < 0 ? 0 : light;
      if (xgMask) {
        xgMask.style.backgroundColor = "rgba(0,0,0,".concat(light, ")");
      }
      pos.light = light;
    }
  }, {
    key: "activeSeekNote",
    value: function activeSeekNote(time) {
      var isForward = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
      var player = this.player, config = this.config;
      var isLive = !(this.duration !== Infinity && this.duration > 0);
      if (!time || typeof time !== "number" || isLive || config.disableActive) {
        return;
      }
      if (time < 0) {
        time = 0;
      } else if (time > player.duration) {
        time = player.duration - 0.2;
      }
      this.changeAction(ACTIONS.SEEKING);
      var startPlugin = player.plugins.start;
      startPlugin && startPlugin.focusHide();
      this.find(".xg-dur").innerHTML = util.format(this.duration);
      this.find(".xg-cur").innerHTML = util.format(time);
      this.find(".xg-curbar").style.width = "".concat(time / this.duration * 100, "%");
      if (isForward) {
        util.removeClass(this.find(".xg-seek-show"), "xg-back");
      } else {
        util.addClass(this.find(".xg-seek-show"), "xg-back");
      }
      this.updateThumbnails(time);
    }
  }, {
    key: "updateThumbnails",
    value: function updateThumbnails(time) {
      var player = this.player;
      var thumbnail = player.plugins.thumbnail;
      if (thumbnail && thumbnail.usable) {
        this.thumbnail && thumbnail.update(this.thumbnail, time, 160, 90);
      }
    }
  }, {
    key: "switchPlayPause",
    value: function switchPlayPause() {
      var player = this.player;
      if (player.state < STATES.ATTACHED) {
        return false;
      } else if (!player.ended) {
        if (player.paused) {
          player.play();
        } else {
          player.pause();
        }
      }
    }
  }, {
    key: "disableGesture",
    value: function disableGesture() {
      this.config.disableGesture = true;
    }
  }, {
    key: "enableGesture",
    value: function enableGesture() {
      this.config.disableGesture = false;
    }
  }, {
    key: "destroy",
    value: function destroy() {
      var player = this.player;
      this.timer && clearTimeout(this.timer);
      this.thumbnail = null;
      player.root.removeChild(this.xgMask);
      this.xgMask = null;
      this.touch && this.touch.destroy();
      this.touch = null;
      player.root.removeEventListener("touchmove", this.onRootTouchMove, true);
      player.root.removeEventListener("touchend", this.onRootTouchEnd, true);
      player.root.removeEventListener("touchcancel", this.onRootTouchEnd, true);
      var controls = this.player.controls;
      if (controls && controls.center) {
        controls.center.removeEventListener("touchmove", this.onRootTouchMove, true);
        controls.center.removeEventListener("touchend", this.onRootTouchEnd, true);
        controls.center.removeEventListener("touchcancel", this.onRootTouchEnd, true);
      }
    }
  }, {
    key: "render",
    value: function render() {
      var className = this.config.gradient !== "normal" ? "gradient ".concat(this.config.gradient) : "gradient";
      return '\n     <xg-trigger class="trigger">\n     <div class="'.concat(className, '"></div>\n        <div class="time-preview">\n            <div class="xg-seek-show ').concat(this.config.disableSeekIcon ? " hide-seek-icon" : "", '">\n              <i class="xg-seek-icon"></i>\n              <span class="xg-cur">00:00</span>\n              <span class="xg-separator">/</span>\n              <span class="xg-dur">00:00</span>\n            </div>\n              <div class="xg-bar xg-timebar">\n                <div class="xg-curbar"></div>\n              </div>\n        </div>\n        <div class="xg-playbackrate xg-top-note">\n            <span><i>').concat(this.config.pressRate, "X</i>").concat(this.i18n.FORWARD, "</span>\n        </div>\n     </xg-trigger>\n    ");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "mobile";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        index: 0,
        disableGesture: false,
        gestureX: true,
        gestureY: true,
        gradient: "normal",
        isTouchingSeek: false,
        miniMoveStep: 5,
        miniYPer: 5,
        scopeL: 0.25,
        scopeR: 0.25,
        scopeM: 0.9,
        pressRate: 2,
        darkness: true,
        maxDarkness: 0.8,
        disableActive: false,
        disableTimeProgress: false,
        hideControlsActive: false,
        hideControlsEnd: false,
        moveDuration: 60 * 6 * 1e3,
        closedbClick: false,
        disablePress: true,
        disableSeekIcon: false,
        focusVideoClick: false
      };
    }
  }]);
  return MobilePlugin2;
}(Plugin);
export { MobilePlugin as default };
