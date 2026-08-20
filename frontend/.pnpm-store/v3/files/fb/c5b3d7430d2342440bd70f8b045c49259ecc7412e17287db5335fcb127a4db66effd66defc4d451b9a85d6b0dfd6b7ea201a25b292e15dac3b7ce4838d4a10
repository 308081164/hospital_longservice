import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass, objectSpread2 as _objectSpread2 } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { LOAD_START, LOADED_DATA, SEEKING, SEEKED, DESTROY, URL_CHANGE, PLAYING, CANPLAY, WAITING, ERROR, RESET, AUTOPLAY_STARTED, TIME_UPDATE, XGLOG } from "../../events.js";
import XG_DEBUG from "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
function now() {
  return new Date().getTime();
}
var LOG_TYPES = {
  LOAD_START: "loadstart",
  LOADED_DATA: "loadeddata",
  FIRST_FRAME: "firstFrame",
  WAIT_START: "waitingStart",
  WAIT_END: "waitingEnd",
  SEEK_START: "seekStart",
  SEEK_END: "seekEnd"
};
var XGLogger = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(XGLogger2, _BasePlugin);
  var _super = _createSuper(XGLogger2);
  function XGLogger2() {
    var _this;
    _classCallCheck(this, XGLogger2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "_onTimeupdate", function() {
      _this._state.isTimeUpdate = true;
      if (_this._state.autoplayStart) {
        XG_DEBUG.logInfo("[xgLogger]".concat(_this.player.playerId, " _onTimeupdate"));
        _this._sendFF("onTimeupdate");
      }
    });
    _defineProperty(_assertThisInitialized(_this), "_onAutoplayStart", function() {
      XG_DEBUG.logInfo("[xgLogger]".concat(_this.player.playerId, " _onAutoplayStart"));
      _this._state.autoplayStart = true;
      _this.vt && _this._sendFF("onAutoplayStart");
    });
    _defineProperty(_assertThisInitialized(_this), "_onReset", function() {
      _this._state = {
        autoplayStart: false,
        isFFLoading: false,
        isTimeUpdate: false,
        isFFSend: false,
        isLs: false
      };
      _this.vt = 0;
      _this.pt = 0;
      _this.fvt = 0;
      _this.newPointTime = now();
      _this.loadedCostTime = 0;
      _this.startCostTime = 0;
      _this._isSeeking = false;
      _this.seekingStart = 0;
      _this.waitingStart = 0;
      _this.fixedWaitingStart = 0;
      _this._isWaiting = false;
      _this._waitTimer && util.clearTimeout(_assertThisInitialized(_this), _this._waitTimer);
      _this._waittTimer && util.clearTimeout(_assertThisInitialized(_this), _this._waittTimer);
      _this._waitTimer = null;
      _this._waittTimer = null;
      _this._waitType = 0;
    });
    _defineProperty(_assertThisInitialized(_this), "_onSeeking", function() {
      if (_this.seekingStart) {
        return;
      }
      _this.suspendWaitingStatus("seek");
      _this.seekingStart = now();
      _this.emitLog(LOG_TYPES.SEEK_START, {
        start: now()
      });
    });
    _defineProperty(_assertThisInitialized(_this), "_onSeeked", function() {
      _this.suspendSeekingStatus("seeked");
    });
    _defineProperty(_assertThisInitialized(_this), "_onWaitingLoadStart", function() {
      if (_this._isWaiting || _this.vt) {
        return;
      }
      _this._isWaiting = true;
      _this.waitingStart = now();
      _this.fixedWaitingStart = now();
      _this._waitType = 1;
      _this.emitLog(LOG_TYPES.WAIT_START, {
        fixedStart: _this.fixedWaitingStart,
        start: _this.waitingStart,
        type: 1,
        endType: "loadstart"
      });
    });
    _defineProperty(_assertThisInitialized(_this), "_onWaiting", function() {
      if (_this._isWaiting || !_this.vt) {
        return;
      }
      _this._isWaiting = true;
      if (!_this.vt) {
        _this._waitType = 1;
      } else if (_this.seekingStart) {
        _this._waitType = 2;
      } else {
        _this._waitType = 0;
      }
      _this.fixedWaitingStart = now();
      _this._waitTimer = util.setTimeout(_assertThisInitialized(_this), function() {
        if (_this._isWaiting) {
          _this.waitingStart = now();
          util.clearTimeout(_assertThisInitialized(_this), _this._waitTimer);
          _this._waitTimer = null;
          _this._startWaitTimeout();
          _this.emitLog(LOG_TYPES.WAIT_START, {
            fixedStart: _this.fixedWaitingStart,
            start: _this.waitingStart,
            type: _this._waitType,
            endType: _this._waitType === 2 ? "seek" : "playing"
          });
        }
      }, 200);
    });
    _defineProperty(_assertThisInitialized(_this), "_onError", function() {
      _this.suspendSeekingStatus("error");
      _this.suspendWaitingStatus("error");
    });
    _defineProperty(_assertThisInitialized(_this), "_onPlaying", function() {
      _this._isWaiting && _this.suspendWaitingStatus("playing");
    });
    return _this;
  }
  _createClass(XGLogger2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      this._onReset();
      this._waitType = "firstFrame";
      this._initOnceEvents();
      this.newPointTime = now();
      this.loadedCostTime = 0;
      this.startCostTime = 0;
      this.on(LOAD_START, function() {
        var _state = _this2._state;
        var autoplayStart = _state.autoplayStart, isFFSend = _state.isFFSend;
        _this2.startCostTime = now() - _this2.newPointTime;
        XG_DEBUG.logInfo("[xgLogger]".concat(_this2.player.playerId, " LOAD_START"), "autoplayStart:".concat(autoplayStart, " isFFSend:").concat(isFFSend, " startCostTime:").concat(_this2.startCostTime, " newPointTime").concat(_this2.newPointTime));
        if (isFFSend) {
          return;
        }
        !_state.isLs && _this2.emitLog(LOG_TYPES.LOAD_START, {});
        _state.isLs = true;
        _state.isTimeUpdate = false;
        _state.isFFLoading = true;
        _this2.pt = now();
        _this2.vt = 0;
        _this2.fvt = 0;
        _this2._initOnceEvents();
        _this2._onWaitingLoadStart();
      });
      this.on(LOADED_DATA, function() {
        _this2.vt = now();
        _this2.fvt = _this2.vt - _this2.pt;
        _this2.loadedCostTime = _this2.vt - _this2.newPointTime;
        var _this2$_state = _this2._state, isTimeUpdate = _this2$_state.isTimeUpdate, isFFSend = _this2$_state.isFFSend, autoplayStart = _this2$_state.autoplayStart;
        XG_DEBUG.logInfo("[xgLogger]".concat(_this2.player.playerId, " LOADED_DATA"), "fvt:".concat(_this2.fvt, " isTimeUpdate:").concat(_this2._state.isTimeUpdate, " loadedCostTime:").concat(_this2.loadedCostTime));
        if (isTimeUpdate || autoplayStart) {
          _this2._sendFF("loadedData");
        }
        if (!isFFSend) {
          _this2.emitLog(LOG_TYPES.LOADED_DATA, {});
        }
        _this2.suspendWaitingStatus("loadeddata");
      });
      this.on(SEEKING, this._onSeeking);
      this.on(SEEKED, this._onSeeked);
      this.on(DESTROY, function() {
        _this2.endState("destroy");
      });
      this.on(URL_CHANGE, function() {
        _this2.endState("urlChange");
        XG_DEBUG.logInfo("[xgLogger]".concat(_this2.player.playerId, " URL_CHANGE"));
        _this2._state.isFFSend && _this2._onReset();
      });
      this.on([PLAYING, CANPLAY], this._onPlaying);
      this.on(WAITING, this._onWaiting);
      this.on(ERROR, this._onError);
      this.on(RESET, function() {
        XG_DEBUG.logInfo("[xgLogger]".concat(_this2.player.playerId, " RESET"));
        _this2.endState("reset");
        _this2._initOnceEvents();
        _this2._onReset();
      });
    }
  }, {
    key: "_initOnceEvents",
    value: function _initOnceEvents() {
      this.off(AUTOPLAY_STARTED, this._onAutoplayStart);
      this.off(TIME_UPDATE, this._onTimeupdate);
      this.once(AUTOPLAY_STARTED, this._onAutoplayStart);
      this.once(TIME_UPDATE, this._onTimeupdate);
    }
  }, {
    key: "_sendFF",
    value: function _sendFF(endType) {
      this.s = now();
      var _this$_state = this._state, isFFLoading = _this$_state.isFFLoading, isFFSend = _this$_state.isFFSend;
      XG_DEBUG.logInfo("[xgLogger]".concat(this.player.playerId, " _sendFF"), "".concat(endType, " fvt:").concat(this.fvt, " isFFLoading:").concat(isFFLoading, " !isFFSend:").concat(!isFFSend));
      if (this.vt > 0 && isFFLoading && !isFFSend) {
        XG_DEBUG.logInfo("[xgLogger]".concat(this.player.playerId, " emitLog_firstFrame"), endType);
        this._state.isFFLoading = false;
        this._state.isFFSend = true;
        this.emitLog(LOG_TYPES.FIRST_FRAME, {
          fvt: this.fvt,
          costTime: this.fvt,
          vt: this.vt,
          startCostTime: this.startCostTime,
          loadedCostTime: this.loadedCostTime
        });
      }
    }
  }, {
    key: "_startWaitTimeout",
    value: function _startWaitTimeout() {
      var _this3 = this;
      if (this._waittTimer) {
        util.clearTimeout(this, this._waittTimer);
      }
      this._waittTimer = util.setTimeout(this, function() {
        _this3.suspendWaitingStatus("timeout");
        util.clearTimeout(_this3, _this3._waittTimer);
        _this3._waittTimer = null;
      }, this.config.waitTimeout);
    }
  }, {
    key: "endState",
    value: function endState(endType) {
      this.suspendWaitingStatus(endType);
      this.suspendSeekingStatus(endType);
    }
  }, {
    key: "suspendSeekingStatus",
    value: function suspendSeekingStatus(endType) {
      if (!this.seekingStart) {
        return;
      }
      var _now = now();
      var _cost = _now - this.seekingStart;
      this.seekingStart = 0;
      this.emitLog(LOG_TYPES.SEEK_END, {
        end: _now,
        costTime: _cost,
        endType
      });
    }
  }, {
    key: "suspendWaitingStatus",
    value: function suspendWaitingStatus(endType) {
      if (this._waitTimer) {
        util.clearTimeout(this, this._waitTimer);
        this._waitTimer = null;
      }
      if (this._waittTimer) {
        util.clearTimeout(this, this._waittTimer);
        this._waittTimer = null;
      }
      this._isWaiting = false;
      if (!this.waitingStart) {
        return;
      }
      var _now = now();
      var _cost = _now - this.waitingStart;
      var _fixedCost = _now - this.fixedWaitingStart;
      var waitTimeout = this.config.waitTimeout;
      this._isWaiting = false;
      this.waitingStart = 0;
      this.fixedWaitingStart = 0;
      this.emitLog(LOG_TYPES.WAIT_END, {
        fixedCostTime: _fixedCost > waitTimeout ? waitTimeout : _fixedCost,
        costTime: _cost > waitTimeout ? waitTimeout : _cost,
        type: endType === "loadeddata" ? 1 : this._waitType,
        endType: this._waitType === 2 ? "seek" : endType
      });
    }
  }, {
    key: "emitLog",
    value: function emitLog(eventType, data) {
      var player = this.player;
      this.emit(XGLOG, _objectSpread2({
        t: now(),
        host: util.getHostFromUrl(player.currentSrc),
        vtype: player.vtype,
        eventType,
        currentTime: this.player.currentTime,
        readyState: player.video.readyState,
        networkState: player.video.networkState
      }, data));
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "xgLogger";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        waitTimeout: 1e4
      };
    }
  }]);
  return XGLogger2;
}(Plugin);
export { LOG_TYPES, XGLogger as default };
