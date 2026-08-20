import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import { WAITING, PLAY, SEEKING } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var GapJump = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(GapJump2, _BasePlugin);
  var _super = _createSuper(GapJump2);
  function GapJump2() {
    var _this;
    _classCallCheck(this, GapJump2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "onGapJump", function() {
      var _assertThisInitialize = _assertThisInitialized(_this), player = _assertThisInitialize.player, config = _assertThisInitialize.config;
      if (player.media.readyState === HTMLMediaElement.HAVE_NOTHING) {
        return;
      }
      if (player.media.seeking) {
        if (!_this.seekingEventReceived) {
          return;
        }
      } else {
        _this.seekingEventReceived = false;
      }
      if (player.media.paused && player.media.currentTime !== 0 && _this.hasPlayed) {
        return;
      }
      var buffered = player.media.buffered;
      var smallGapLimit = config.smallGapLimit || 0.5;
      var gapDetectionThreshold = config.gapDetectionThreshold || 0.3;
      var currentTime = player.media.currentTime;
      var idx = _this._getIndex(buffered, currentTime, gapDetectionThreshold);
      if (idx === null || idx === 0) {
        return;
      }
      console.log("GapJump  bufferRange ", buffered.start(idx), buffered.end(idx));
      var jumpTo = buffered.start(idx) + 0.1;
      var seekEnd = player.media.duration;
      if (jumpTo > seekEnd) {
        return;
      }
      var jumpSize = jumpTo - currentTime;
      var isGapSmall = jumpSize <= smallGapLimit;
      if (jumpSize < GapJump2.BROWSER_GAP_TOLERANCE) {
        return;
      }
      if (isGapSmall) {
        if (config.useGapJump !== false) {
          player.media.currentTime = _this.isSafari ? jumpTo + 0.1 : jumpTo;
        }
        _this.player && _this.player.emit("detectGap");
        console.log("gapJump gapIndex", idx, " isGapSamll:", isGapSmall, " currentTime:", player.media.currentTime, " jumpSize:", currentTime - player.media.currentTime);
        if (jumpTo !== 0.08) {
          player && player.emit("log", {
            type: "oneevent",
            end_type: "gap",
            vid: player.config.vid,
            ext: {
              video_postion: Math.floor(jumpTo * 1e3)
            }
          });
        }
      }
    });
    return _this;
  }
  _createClass(GapJump2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      var useGapJump = this.config.useGapJump;
      if (useGapJump === false) {
        return;
      }
      this.hasPlayed = false;
      this.seekingEventReceived = false;
      this.isSafari = /(Safari|iPhone|iPad|iPod)/.test(navigator.userAgent) && !/Chrome/.test(navigator.userAgent) && !/BlackBerry/.test(navigator.platform);
      this.on(WAITING, this.onGapJump);
      this.on(PLAY, function() {
        _this2.hasPlayed = true;
      });
      this.on(SEEKING, function() {
        _this2.seekingEventReceived = true;
      });
    }
  }, {
    key: "_getIndex",
    value: function _getIndex(buffered, time, threshold) {
      if (!buffered || !buffered.length) {
        return null;
      }
      if (buffered.length === 1 && buffered.end(0) - buffered.start(0) < 1e-6) {
        return null;
      }
      var bufferedInfo = this._getBuffered(buffered);
      var idx = null;
      for (var i = 0; i < bufferedInfo.length; i++) {
        var item = bufferedInfo[i];
        if (item.start > time && (i === 0 || bufferedInfo[i - 1].end - time <= threshold)) {
          idx = i;
          break;
        }
      }
      return idx;
    }
  }, {
    key: "_getBuffered",
    value: function _getBuffered(b) {
      if (!b) {
        return [];
      }
      var ret = [];
      for (var i = 0; i < b.length; i++) {
        ret.push({
          start: b.start(i),
          end: b.end(i)
        });
      }
      return ret;
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "gapJump";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        useGapJump: false,
        smallGapLimit: 0.5,
        gapDetectionThreshold: 0.3
      };
    }
  }]);
  return GapJump2;
}(Plugin);
GapJump.BROWSER_GAP_TOLERANCE = 1e-3;
export { GapJump as default };
