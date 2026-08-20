import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, objectSpread2 as _objectSpread2, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import { USER_ACTION, STATS_EVENTS as STATS_EVENTS$1 } from "../../events.js";
import BasePlugin from "../../plugin/basePlugin.js";
import "delegate";
var INFO = "info";
var STATS_EVENTS = STATS_EVENTS$1;
var Stats = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(Stats2, _BasePlugin);
  var _super = _createSuper(Stats2);
  function Stats2() {
    var _this;
    _classCallCheck(this, Stats2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "_recordUserActions", function(actionData) {
      var time = _this._getTime();
      var payload = Object.assign({}, actionData, {
        msg: actionData.msg || actionData.action
      });
      _this._stats[INFO].push(_objectSpread2(_objectSpread2({
        type: "userAction"
      }, time), {}, {
        payload
      }));
    });
    _defineProperty(_assertThisInitialized(_this), "_onReset", function() {
      _this.reset();
    });
    _defineProperty(_assertThisInitialized(_this), "_recordInfo", function(data) {
      _this.info(data);
    });
    _defineProperty(_assertThisInitialized(_this), "_downloadStats", function() {
      var stats = _this.getStats();
      var blob = new Blob([JSON.stringify(stats)], {
        type: "application/json"
      });
      var downloadUrl = window.URL.createObjectURL(blob);
      var link = document.createElement("a");
      link.style.display = "none";
      link.href = downloadUrl;
      link.download = "player.txt";
      link.disabled = false;
      link.click();
    });
    return _this;
  }
  _createClass(Stats2, [{
    key: "_getTime",
    value: function _getTime() {
      return {
        timestamp: Date.now(),
        timeFormat: new Date().toISOString()
      };
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      this.reset();
      this.on(USER_ACTION, this._recordUserActions);
      this.on(STATS_EVENTS.STATS_INFO, this._recordInfo);
      this.on(STATS_EVENTS.STATS_DOWNLOAD, this._downloadStats);
      this.on(STATS_EVENTS.STATS_RESET, this._onReset);
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.offAll();
    }
  }, {
    key: "downloadStats",
    value: function downloadStats() {
      this._downloadStats();
    }
  }, {
    key: "info",
    value: function info(data) {
      if (data.profile) {
        this._infoProfile(data);
      } else {
        this._info(data);
      }
    }
  }, {
    key: "_info",
    value: function _info(data) {
      var time = this._getTime();
      this._stats[INFO].push(_objectSpread2(_objectSpread2({}, time), {}, {
        payload: data
      }));
    }
  }, {
    key: "_infoProfile",
    value: function _infoProfile(data) {
      if (data && data.startMs) {
        var endMs = Date.now();
        var dur = endMs - data.startMs;
        var profile = _objectSpread2({
          cat: "function",
          dur,
          name: data.name || data.msg,
          ph: "X",
          pid: 0,
          tid: 0,
          ts: data.startMs,
          profile: true
        }, data);
        this._info(profile);
      } else {
        console.warn("infoProfile need object data, include startMs");
      }
    }
  }, {
    key: "reset",
    value: function reset() {
      var _this$_stats;
      this._stats = (_this$_stats = {}, _defineProperty(_this$_stats, INFO, []), _defineProperty(_this$_stats, "media", {}), _this$_stats);
    }
  }, {
    key: "getStats",
    value: function getStats() {
      var player = this.player;
      var mediaElem = player.media;
      var buffered = [];
      for (var i = 0; i < mediaElem.buffered.length; i++) {
        buffered.push({
          start: mediaElem.buffered.start(i),
          end: mediaElem.buffered.end(i)
        });
      }
      var mediaInfo = {
        currentTime: mediaElem.currentTime,
        readyState: mediaElem.readyState,
        buffered,
        paused: mediaElem.paused,
        ended: mediaElem.ended
      };
      this._stats.media = mediaInfo;
      return {
        raw: this._stats,
        timestat: this._getTimeStats(),
        profile: this._getProfile()
      };
    }
  }, {
    key: "_getTimeStats",
    value: function _getTimeStats() {
      var allStat = this._stats[INFO];
      var msgs = allStat.map(function(stat) {
        var data = stat.payload.data;
        var msg = "";
        try {
          if (data instanceof Error) {
            msg = data.msg;
          } else if (data !== void 0) {
            msg = JSON.stringify(data);
          }
        } catch (error) {
          console.log("err", error);
        }
        return "[".concat(stat.timeFormat, "] : ").concat(stat.payload.msg, " ").concat(msg, " ");
      });
      return msgs;
    }
  }, {
    key: "_getProfile",
    value: function _getProfile() {
      var profile = {
        traceEvents: []
      };
      var allStat = this._stats[INFO];
      allStat.forEach(function(stat) {
        if (stat.payload.profile) {
          profile.traceEvents.push(stat.payload);
        }
      });
      return profile;
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "stats";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {};
    }
  }]);
  return Stats2;
}(BasePlugin);
export { Stats as default };
