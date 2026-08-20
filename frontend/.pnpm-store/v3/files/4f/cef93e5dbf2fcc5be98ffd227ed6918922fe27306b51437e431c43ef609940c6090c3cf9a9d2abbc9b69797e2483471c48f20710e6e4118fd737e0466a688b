import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, objectSpread2 as _objectSpread2, typeof as _typeof } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import { DURATION_CHANGE, TIME_UPDATE, SEEKED, PROGRESS, ENDED, EMPTIED, VIDEO_RESIZE } from "../../events.js";
import "../../utils/debug.js";
import Plugin, { POSITIONS } from "../../plugin/plugin.js";
import InnerList from "./innerList.js";
var FRAGMENT_FOCUS_CLASS = {
  POINT: "inner-focus-point",
  HIGHLIGHT: "inner-focus-highlight"
};
var Progress = /* @__PURE__ */ function(_Plugin) {
  _inherits(Progress2, _Plugin);
  var _super = _createSuper(Progress2);
  function Progress2(args) {
    var _this;
    _classCallCheck(this, Progress2);
    _this = _super.call(this, args);
    _defineProperty(_assertThisInitialized(_this), "onMoveOnly", function(e, data) {
      var _assertThisInitialize = _assertThisInitialized(_this), pos = _assertThisInitialize.pos, config = _assertThisInitialize.config, player = _assertThisInitialize.player;
      var ret = data;
      if (e) {
        util.event(e);
        var _ePos = util.getEventPos(e, player.zoom);
        var x = player.rotateDeg === 90 ? _ePos.clientY : _ePos.clientX;
        if (pos.moving && Math.abs(pos.x - x) < config.miniMoveStep) {
          return;
        }
        pos.moving = true;
        pos.x = x;
        ret = _this.computeTime(e, x);
      }
      _this.triggerCallbacks("dragmove", ret, e);
      _this._updateInnerFocus(ret);
    });
    _defineProperty(_assertThisInitialized(_this), "onBodyClick", function(e) {
      if (!_this.pos.isLocked) {
        return;
      }
      _this.pos.isLocked = false;
      e.preventDefault();
      e.stopPropagation();
    });
    _defineProperty(_assertThisInitialized(_this), "_mouseDownHandler", function(event, data) {
      _this._state.time = data.currentTime;
      _this.updateWidth(data.currentTime, data.seekTime, data.percent, 0);
      _this._updateInnerFocus(data);
    });
    _defineProperty(_assertThisInitialized(_this), "_mouseUpHandler", function(e, data) {
      var _assertThisInitialize2 = _assertThisInitialized(_this), pos = _assertThisInitialize2.pos;
      pos.moving && _this.updateWidth(data.currentTime, data.seekTime, data.percent, 2);
    });
    _defineProperty(_assertThisInitialized(_this), "_mouseMoveHandler", function(e, data) {
      var _assertThisInitialize3 = _assertThisInitialized(_this), _state = _assertThisInitialize3._state, pos = _assertThisInitialize3.pos, config = _assertThisInitialize3.config, player = _assertThisInitialize3.player;
      if (_state.time < data.currentTime) {
        data.forward = true;
      } else {
        data.forward = false;
      }
      _state.time = data.currentTime;
      if (pos.isDown && !pos.moving) {
        pos.moving = true;
        config.isPauseMoving && player.pause();
        _this.triggerCallbacks("dragstart", data, e);
        _this.emitUserAction("drag", "dragstart", data);
      }
      _this.updateWidth(data.currentTime, data.seekTime, data.percent, 1);
      _this.triggerCallbacks("dragmove", data, e);
      _this._updateInnerFocus(data);
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseDown", function(e) {
      var _assertThisInitialize4 = _assertThisInitialized(_this), _state = _assertThisInitialize4._state, player = _assertThisInitialize4.player, pos = _assertThisInitialize4.pos, config = _assertThisInitialize4.config, playerConfig = _assertThisInitialize4.playerConfig;
      var _ePos = util.getEventPos(e, player.zoom);
      var x = player.rotateDeg === 90 ? _ePos.clientY : _ePos.clientX;
      if (player.isMini || config.closeMoveSeek || !playerConfig.allowSeekAfterEnded && player.ended) {
        return;
      }
      if (!player.duration && !player.isPlaying) {
        player.play();
        return;
      }
      e.stopPropagation();
      _this.focus();
      util.checkIsFunction(playerConfig.disableSwipeHandler) && playerConfig.disableSwipeHandler();
      util.checkIsFunction(config.onMoveStart) && config.onMoveStart();
      util.event(e);
      pos.x = x;
      pos.isDown = true;
      pos.moving = false;
      _state.prePlayTime = player.currentTime;
      player.focus({
        autoHide: false
      });
      _this.isProgressMoving = true;
      util.addClass(_this.progressBtn, "active");
      var ret = _this.computeTime(e, x);
      ret.prePlayTime = _state.prePlayTime;
      _this._mouseDownHandlerHook(e, ret);
      var eventType = e.type;
      if (eventType === "touchstart") {
        _this.root.addEventListener("touchmove", _this.onMouseMove);
        _this.root.addEventListener("touchend", _this.onMouseUp);
        _this.root.addEventListener("touchcancel", _this.onMouseUp);
      } else {
        _this.unbind("mousemove", _this.onMoveOnly);
        document.addEventListener("mousemove", _this.onMouseMove, false);
        document.addEventListener("mouseup", _this.onMouseUp, false);
      }
      return true;
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseUp", function(e) {
      var _assertThisInitialize5 = _assertThisInitialized(_this), player = _assertThisInitialize5.player, config = _assertThisInitialize5.config, pos = _assertThisInitialize5.pos, playerConfig = _assertThisInitialize5.playerConfig, _state = _assertThisInitialize5._state;
      if (!pos) {
        return;
      }
      e.stopPropagation();
      e.preventDefault();
      util.checkIsFunction(playerConfig.enableSwipeHandler) && playerConfig.enableSwipeHandler();
      util.checkIsFunction(config.onMoveEnd) && config.onMoveEnd();
      util.event(e);
      util.removeClass(_this.progressBtn, "active");
      var ret = _this.computeTime(e, pos.x);
      ret.prePlayTime = _state.prePlayTime;
      if (pos.moving) {
        _this.triggerCallbacks("dragend", ret, e);
        _this.emitUserAction("drag", "dragend", ret);
      } else {
        _this.triggerCallbacks("click", ret, e);
        _this.emitUserAction("click", "click", ret);
      }
      _this._mouseUpHandlerHook(e, ret);
      pos.moving = false;
      pos.isDown = false;
      pos.x = 0;
      pos.y = 0;
      pos.isLocked = true;
      _state.prePlayTime = 0;
      _state.time = 0;
      var eventType = e.type;
      if (eventType === "touchend" || eventType === "touchcancel") {
        _this.root.removeEventListener("touchmove", _this.onMouseMove);
        _this.root.removeEventListener("touchend", _this.onMouseUp);
        _this.root.removeEventListener("touchcancel", _this.onMouseUp);
        _this.blur();
      } else {
        document.removeEventListener("mousemove", _this.onMouseMove, false);
        document.removeEventListener("mouseup", _this.onMouseUp, false);
        if (!pos.isEnter) {
          _this.onMouseLeave(e);
        } else {
          playerConfig.isMobileSimulateMode !== "mobile" && _this.bind("mousemove", _this.onMoveOnly);
        }
      }
      util.setTimeout(_assertThisInitialized(_this), function() {
        _this.resetSeekState();
      }, 1);
      player.focus();
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseMove", function(e) {
      var _assertThisInitialize6 = _assertThisInitialized(_this), _state = _assertThisInitialize6._state, pos = _assertThisInitialize6.pos, player = _assertThisInitialize6.player, config = _assertThisInitialize6.config;
      if (util.checkTouchSupport()) {
        e.preventDefault();
      }
      util.event(e);
      var _ePos = util.getEventPos(e, player.zoom);
      var x = player.rotateDeg === 90 ? _ePos.clientY : _ePos.clientX;
      var diff = Math.abs(pos.x - x);
      if (pos.moving && diff < config.miniMoveStep || !pos.moving && diff < config.miniStartStep) {
        return;
      }
      pos.x = x;
      var ret = _this.computeTime(e, x);
      ret.prePlayTime = _state.prePlayTime;
      _this._mouseMoveHandlerHook(e, ret);
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseOut", function(e) {
      _this.triggerCallbacks("mouseout", null, e);
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseOver", function(e) {
      _this.triggerCallbacks("mouseover", null, e);
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseEnter", function(e) {
      var _assertThisInitialize7 = _assertThisInitialized(_this), player = _assertThisInitialize7.player, pos = _assertThisInitialize7.pos;
      if (pos.isDown || pos.isEnter || player.isMini || !player.config.allowSeekAfterEnded && player.ended) {
        return;
      }
      pos.isEnter = true;
      _this.bind("mousemove", _this.onMoveOnly);
      _this.bind("mouseleave", _this.onMouseLeave);
      util.event(e);
      var _ePos = util.getEventPos(e, player.zoom);
      var x = player.rotateDeg === 90 ? _ePos.clientY : _ePos.clientX;
      var ret = _this.computeTime(e, x);
      _this.triggerCallbacks("mouseenter", ret, e);
      _this.focus();
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseLeave", function(e) {
      _this.triggerCallbacks("mouseleave", null, e);
      _this.unlock();
      _this._updateInnerFocus(null);
    });
    _defineProperty(_assertThisInitialized(_this), "onVideoResize", function() {
      var _this$pos = _this.pos, x = _this$pos.x, isDown = _this$pos.isDown, isEnter = _this$pos.isEnter;
      if (isEnter && !isDown) {
        var ret = _this.computeTime(null, x);
        _this.onMoveOnly(null, ret);
      }
    });
    _this.useable = false;
    _this.isProgressMoving = false;
    _this.__dragCallBacks = [];
    _this._state = {
      now: -1,
      direc: 0,
      time: 0,
      prePlayTime: -1
    };
    _this._disableBlur = false;
    if (typeof _this.config.isDragingSeek === "boolean") {
      console.warn("[XGPLAYER] 'isDragingSeek' is deprecated, please use 'isDraggingSeek' instead");
      _this.config.isDraggingSeek = _this.config.isDragingSeek;
    }
    return _this;
  }
  _createClass(Progress2, [{
    key: "offsetDuration",
    get: function get() {
      return this.playerConfig.customDuration || this.player.offsetDuration || this.player.duration;
    }
  }, {
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
    key: "currentTime",
    get: function get() {
      var _this$player = this.player, offsetCurrentTime = _this$player.offsetCurrentTime, currentTime = _this$player.currentTime;
      return offsetCurrentTime >= 0 ? offsetCurrentTime : currentTime + this.timeOffset;
    }
  }, {
    key: "changeState",
    value: function changeState() {
      var useable = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : true;
      this.useable = useable;
    }
  }, {
    key: "show",
    value: function show(value) {
      this.root && (this.root.style.display = "flex");
    }
  }, {
    key: "_initInner",
    value: function _initInner() {
      var _this2 = this;
      var fragments = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : [];
      var config = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
      if (!fragments || fragments.length === 0) {
        fragments = [{
          percent: 1
        }];
      }
      var _c = _objectSpread2(_objectSpread2({
        fragments
      }, config), {}, {
        actionCallback: function actionCallback(data) {
          _this2.emitUserAction("fragment_focus", "fragment_focus", data);
        }
      });
      if (!this.innerList) {
        this.innerList = new InnerList(_c);
        this.outer.insertBefore(this.innerList.render(), this.outer.children[0]);
        ["findHightLight", "unHightLight", "setHightLight", "findFragment"].map(function(item) {
          _this2[item] = _this2.innerList[item].bind(_this2.innerList);
        });
      } else {
        this.innerList.reset(_c);
      }
    }
  }, {
    key: "_updateInnerFocus",
    value: function _updateInnerFocus(data) {
      this.innerList && this.innerList.updateFocus(data);
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      if (this.config.disable || this.playerConfig.isLive) {
        return;
      }
      this.pos = {
        x: 0,
        y: 0,
        moving: false,
        isDown: false,
        isEnter: false,
        isLocked: false
      };
      this.outer = this.find("xg-outer");
      var _this$config = this.config, fragFocusClass = _this$config.fragFocusClass, fragAutoFocus = _this$config.fragAutoFocus, fragClass = _this$config.fragClass;
      this._initInner(this.config.fragments, {
        fragFocusClass,
        fragAutoFocus,
        fragClass,
        style: this.playerConfig.commonStyle || {}
      });
      if (sniffer.device === "mobile") {
        this.config.isDraggingSeek = false;
        this.isMobile = true;
      }
      this.progressBtn = this.find(".xgplayer-progress-btn");
      this.listenEvents();
      this.bindDomEvents();
      this.initCustomStyle();
    }
  }, {
    key: "listenEvents",
    value: function listenEvents() {
      var _this3 = this;
      this.on(DURATION_CHANGE, function() {
        _this3.onMouseLeave();
      });
      this.on(TIME_UPDATE, function() {
        _this3.onTimeupdate();
      });
      this.on(SEEKED, function() {
        _this3.onTimeupdate();
        _this3.onCacheUpdate();
      });
      this.on(PROGRESS, function() {
        _this3.onCacheUpdate();
      });
      this.on(ENDED, function() {
        _this3.onCacheUpdate(true);
        _this3.onTimeupdate(true);
        _this3._state.now = 0;
      });
      this.on(EMPTIED, function() {
        _this3.onReset();
      });
      this.on(VIDEO_RESIZE, function() {
        _this3.onVideoResize();
      });
    }
  }, {
    key: "setConfig",
    value: function setConfig(config) {
      var _this4 = this;
      var frags = null;
      Object.keys(config).forEach(function(key) {
        _this4.config[key] = config[key];
        if (key === "fragments") {
          frags = config[key];
        }
      });
      if (frags) {
        this._initInner(frags, config);
      }
    }
  }, {
    key: "initCustomStyle",
    value: function initCustomStyle() {
      var _ref = this.playerConfig || {}, commonStyle = _ref.commonStyle;
      var sliderBtnStyle = commonStyle.sliderBtnStyle;
      var progressBtn = this.progressBtn;
      if (sliderBtnStyle) {
        if (typeof sliderBtnStyle === "string") {
          progressBtn.style.boxShadow = sliderBtnStyle;
        } else if (_typeof(sliderBtnStyle) === "object") {
          Object.keys(sliderBtnStyle).map(function(key) {
            progressBtn.style[key] = sliderBtnStyle[key];
          });
        }
      }
    }
  }, {
    key: "triggerCallbacks",
    value: function triggerCallbacks(type, data, event) {
      if (this.__dragCallBacks.length > 0) {
        this.__dragCallBacks.map(function(item) {
          if (item && item.handler && item.type === type) {
            try {
              item.handler(data, event);
            } catch (error) {
              console.error("[XGPLAYER][triggerCallbacks] ".concat(item, " error"), error);
            }
          }
        });
      }
    }
  }, {
    key: "addCallBack",
    value: function addCallBack(type, handle) {
      if (handle && typeof handle === "function") {
        this.__dragCallBacks.push({
          type,
          handler: handle
        });
      }
    }
  }, {
    key: "removeCallBack",
    value: function removeCallBack(type, event) {
      var __dragCallBacks = this.__dragCallBacks;
      var _index = -1;
      __dragCallBacks.map(function(item, index) {
        if (item && item.type === type && item.handler === event) {
          _index = index;
        }
      });
      if (_index > -1) {
        __dragCallBacks.splice(_index, 1);
      }
    }
  }, {
    key: "unlock",
    value: function unlock() {
      var player = this.player, pos = this.pos;
      pos.isEnter = false;
      pos.isLocked = false;
      if (player.isMini) {
        return;
      }
      this.unbind("mousemove", this.onMoveOnly);
      if (pos.isDown) {
        this.unbind("mouseleave", this.onMouseLeave);
        return;
      }
      this.blur();
    }
  }, {
    key: "bindDomEvents",
    value: function bindDomEvents() {
      var config = this.player.config;
      this._mouseDownHandlerHook = this.hook("dragstart", this._mouseDownHandler);
      this._mouseUpHandlerHook = this.hook("dragend", this._mouseUpHandler);
      this._mouseMoveHandlerHook = this.hook("drag", this._mouseMoveHandler);
      if (this.domEventType === "touch" || this.domEventType === "compatible") {
        this.root.addEventListener("touchstart", this.onMouseDown);
      }
      if (this.domEventType === "mouse" || this.domEventType === "compatible") {
        this.bind("mousedown", this.onMouseDown);
        config.isMobileSimulateMode !== "mobile" && this.bind("mouseenter", this.onMouseEnter);
        this.bind("mouseover", this.onMouseOver);
        this.bind("mouseout", this.onMouseOut);
        this.player.root.addEventListener("click", this.onBodyClick, true);
      }
    }
  }, {
    key: "focus",
    value: function focus() {
      this.player.controls.pauseAutoHide();
      util.addClass(this.root, "active");
    }
  }, {
    key: "blur",
    value: function blur() {
      if (this._disableBlur) {
        return;
      }
      this.player.controls.recoverAutoHide();
      util.removeClass(this.root, "active");
    }
  }, {
    key: "disableBlur",
    value: function disableBlur() {
      this._disableBlur = true;
    }
  }, {
    key: "enableBlur",
    value: function enableBlur() {
      this._disableBlur = false;
    }
  }, {
    key: "updateWidth",
    value: function updateWidth(currentTime, seekTime, percent, type) {
      var config = this.config, player = this.player;
      if (config.isCloseClickSeek && type === 0) {
        return;
      }
      var realTime = seekTime = seekTime >= player.duration ? player.duration - config.endedDiff : Number(seekTime).toFixed(1);
      this.updatePercent(percent);
      this.updateTime(currentTime);
      if (type === 1 && (!config.isDraggingSeek || player.config.mediaType === "audio")) {
        return;
      }
      this._state.now = realTime;
      this._state.direc = realTime > player.currentTime ? 0 : 1;
      player.seek(realTime);
    }
  }, {
    key: "computeTime",
    value: function computeTime(e, x) {
      var player = this.player;
      var _this$root$getBoundin = this.root.getBoundingClientRect(), width = _this$root$getBoundin.width, height = _this$root$getBoundin.height, top = _this$root$getBoundin.top, left = _this$root$getBoundin.left;
      var rWidth, rLeft;
      var clientX = x;
      if (player.rotateDeg === 90) {
        rWidth = height;
        rLeft = top;
      } else {
        rWidth = width;
        rLeft = left;
      }
      var offset = clientX - rLeft;
      offset = offset > rWidth ? rWidth : offset < 0 ? 0 : offset;
      var percent = offset / rWidth;
      percent = percent < 0 ? 0 : percent > 1 ? 1 : percent;
      if (Number.isNaN(percent)) {
        percent = this.player.currentTime / this.offsetDuration;
      }
      var currentTime = parseInt(percent * this.offsetDuration * 1e3, 10) / 1e3;
      var seekTime = util.getCurrentTimeByOffset(currentTime, player.timeSegments);
      return {
        percent,
        currentTime,
        seekTime,
        offset,
        width: rWidth,
        left: rLeft,
        e
      };
    }
  }, {
    key: "updateTime",
    value: function updateTime(time) {
      var player = this.player, duration = this.duration;
      if (time > duration) {
        time = duration;
      } else if (time < 0) {
        time = 0;
      }
      var timeIcon = player.plugins.time;
      if (timeIcon) {
        timeIcon.updateTime(time);
      }
    }
  }, {
    key: "resetSeekState",
    value: function resetSeekState() {
      this.isProgressMoving = false;
      var timeIcon = this.player.plugins.time;
      timeIcon && timeIcon.resetActive();
    }
  }, {
    key: "updatePercent",
    value: function updatePercent(percent, notSeek) {
      this.isProgressMoving = true;
      if (this.config.disable) {
        return;
      }
      percent = percent > 1 ? 1 : percent < 0 ? 0 : percent;
      this.progressBtn.style.left = "".concat(percent * 100, "%");
      this.innerList.update({
        played: percent * this.offsetDuration
      }, this.offsetDuration);
      var miniprogress = this.player.plugins.miniprogress;
      miniprogress && miniprogress.update({
        played: percent * this.offsetDuration
      }, this.offsetDuration);
    }
  }, {
    key: "onTimeupdate",
    value: function onTimeupdate(isEnded) {
      var player = this.player, _state = this._state, offsetDuration = this.offsetDuration;
      if (player.isSeeking && player.media.seeking || this.isProgressMoving || !player.hasStart) {
        return;
      }
      if (_state.now > -1) {
        var abs = parseInt(_state.now * 1e3, 10) - parseInt(player.currentTime * 1e3, 10);
        if (_state.direc === 0 && abs > 300 || _state.direc === 1 && abs > -300) {
          _state.now = -1;
          return;
        } else {
          _state.now = -1;
        }
      }
      var time = this.currentTime;
      time = util.adjustTimeByDuration(time, offsetDuration, isEnded);
      this.innerList.update({
        played: time
      }, offsetDuration);
      this.progressBtn.style.left = "".concat(time / offsetDuration * 100, "%");
    }
  }, {
    key: "onCacheUpdate",
    value: function onCacheUpdate(isEnded) {
      var player = this.player, duration = this.duration;
      if (!player) {
        return;
      }
      var _end = player.bufferedPoint.end;
      _end = util.adjustTimeByDuration(_end, duration, isEnded);
      this.innerList.update({
        cached: _end
      }, duration);
    }
  }, {
    key: "onReset",
    value: function onReset() {
      this.innerList.update({
        played: 0,
        cached: 0
      }, 0);
      this.progressBtn.style.left = "0%";
    }
  }, {
    key: "destroy",
    value: function destroy() {
      var player = this.player;
      this.thumbnailPlugin = null;
      this.innerList.destroy();
      this.innerList = null;
      var domEventType = this.domEventType;
      if (domEventType === "touch" || domEventType === "compatible") {
        this.root.removeEventListener("touchstart", this.onMouseDown);
        this.root.removeEventListener("touchmove", this.onMouseMove);
        this.root.removeEventListener("touchend", this.onMouseUp);
        this.root.removeEventListener("touchcancel", this.onMouseUp);
      }
      if (domEventType === "mouse" || domEventType === "compatible") {
        this.unbind("mousedown", this.onMouseDown);
        this.unbind("mouseenter", this.onMouseEnter);
        this.unbind("mousemove", this.onMoveOnly);
        this.unbind("mouseleave", this.onMouseLeave);
        document.removeEventListener("mousemove", this.onMouseMove, false);
        document.removeEventListener("mouseup", this.onMouseUp, false);
        player.root.removeEventListener("click", this.onBodyClick, true);
      }
    }
  }, {
    key: "render",
    value: function render() {
      if (this.config.disable || this.playerConfig.isLive) {
        return;
      }
      var controlsMode = this.player.controls ? this.player.controls.config.mode : "";
      var className = controlsMode === "bottom" ? "xgplayer-progress-bottom" : "";
      return '\n    <xg-progress class="xgplayer-progress '.concat(className, '">\n      <xg-outer class="xgplayer-progress-outer">\n        <xg-progress-btn class="xgplayer-progress-btn"></xg-progress-btn>\n      </xg-outer>\n    </xg-progress>\n    ');
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "progress";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_CENTER,
        index: 0,
        disable: false,
        isDraggingSeek: true,
        closeMoveSeek: false,
        isPauseMoving: false,
        isCloseClickSeek: false,
        fragments: [{
          percent: 1
        }],
        fragFocusClass: FRAGMENT_FOCUS_CLASS.POINT,
        fragClass: "",
        fragAutoFocus: false,
        miniMoveStep: 5,
        miniStartStep: 2,
        onMoveStart: function onMoveStart() {
        },
        onMoveEnd: function onMoveEnd() {
        },
        endedDiff: 0.2
      };
    }
  }, {
    key: "FRAGMENT_FOCUS_CLASS",
    get: function get() {
      return FRAGMENT_FOCUS_CLASS;
    }
  }]);
  return Progress2;
}(Plugin);
export { Progress as default };
