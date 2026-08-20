import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import { PLAY, PAUSE, ENDED, EMPTIED, FPS_STUCK } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var FpsDetect = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(FpsDetect2, _BasePlugin);
  var _super = _createSuper(FpsDetect2);
  function FpsDetect2() {
    _classCallCheck(this, FpsDetect2);
    return _super.apply(this, arguments);
  }
  _createClass(FpsDetect2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this = this;
      var player = this.player, config = this.config;
      var media = player.media || player.video;
      this.timer = null;
      this._lastDecodedFrames = 0;
      this._currentStuckCount = 0;
      this._lastCheckPoint = null;
      this._payload = [];
      if (config.disabled)
        return;
      var getVideoPlaybackQuality = media.getVideoPlaybackQuality;
      if (!getVideoPlaybackQuality)
        return;
      this.on(PLAY, function() {
        _this._startTick();
      });
      this.on(PAUSE, function() {
        _this._stopTick();
      });
      this.on(ENDED, function() {
        _this._stopTick();
      });
      this.on(EMPTIED, function() {
        _this._stopTick();
      });
    }
  }, {
    key: "_startTick",
    value: function _startTick() {
      var _this2 = this;
      this._stopTick();
      this._timer = setTimeout(function() {
        _this2._checkDecodeFPS();
        _this2._startTick();
      }, this.config.tick);
    }
  }, {
    key: "_stopTick",
    value: function _stopTick() {
      clearTimeout(this._timer);
      this._timer = null;
    }
  }, {
    key: "_checkBuffer",
    value: function _checkBuffer(curTime, buffered) {
      var enoughBuffer = false;
      var buffers = [];
      for (var i = 0; i < buffered.length; i++) {
        var start = buffered.start(i);
        var end = buffered.end(i);
        buffers.push({
          start,
          end
        });
        if (start <= curTime && curTime <= end - 1) {
          enoughBuffer = true;
          break;
        }
      }
      return {
        enoughBuffer,
        buffers
      };
    }
  }, {
    key: "_checkStuck",
    value: function _checkStuck(curDecodedFrames, totalVideoFrames, droppedVideoFrames, checkInterval) {
      var media = this.player.media || this.player.video;
      var hidden = document.hidden;
      var paused = media.paused, readyState = media.readyState, currentTime = media.currentTime, buffered = media.buffered;
      if (hidden || paused || readyState < 4) {
        return;
      }
      var _this$_checkBuffer = this._checkBuffer(currentTime, buffered), enoughBuffer = _this$_checkBuffer.enoughBuffer, buffers = _this$_checkBuffer.buffers;
      if (!enoughBuffer) {
        return;
      }
      if (curDecodedFrames <= this.config.reportFrame) {
        this._currentStuckCount++;
        this._payload.push({
          currentTime,
          buffers,
          curDecodedFrames,
          totalVideoFrames,
          droppedVideoFrames,
          checkInterval
        });
        if (this._currentStuckCount >= this.config.stuckCount) {
          this.emit(FPS_STUCK, this._payload);
          this._reset();
        }
      } else {
        this._reset();
      }
    }
  }, {
    key: "_reset",
    value: function _reset() {
      this._payload = [];
      this._currentStuckCount = 0;
    }
  }, {
    key: "_checkDecodeFPS",
    value: function _checkDecodeFPS() {
      var media = this.player.media || this.player.video;
      if (!media) {
        return;
      }
      var _media$getVideoPlayba = media.getVideoPlaybackQuality(), totalVideoFrames = _media$getVideoPlayba.totalVideoFrames, droppedVideoFrames = _media$getVideoPlayba.droppedVideoFrames;
      var currTime = performance.now();
      if (totalVideoFrames) {
        if (this._lastCheckPoint) {
          var curDecoded = totalVideoFrames - this._lastDecodedFrames;
          var checkInterval = currTime - this._lastCheckPoint;
          this._checkStuck(curDecoded, totalVideoFrames, droppedVideoFrames, checkInterval);
        }
      }
      this._lastDecodedFrames = totalVideoFrames;
      this._lastCheckPoint = currTime;
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this._stopTick();
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "FpsDetect";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        disabled: false,
        tick: 1e3,
        stuckCount: 3,
        reportFrame: 0
      };
    }
  }]);
  return FpsDetect2;
}(Plugin);
export { FpsDetect as default };
