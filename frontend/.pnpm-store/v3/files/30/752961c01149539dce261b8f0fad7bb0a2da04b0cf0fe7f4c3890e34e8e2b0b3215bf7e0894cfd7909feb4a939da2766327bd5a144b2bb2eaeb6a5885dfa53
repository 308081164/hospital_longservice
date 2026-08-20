import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { DURATION_CHANGE, LOADED_DATA, TIME_UPDATE, SEEKING, PLAY } from "../../events.js";
import BasePlugin from "../../plugin/basePlugin.js";
import "delegate";
var TimeSegmentsControls = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(TimeSegmentsControls2, _BasePlugin);
  var _super = _createSuper(TimeSegmentsControls2);
  function TimeSegmentsControls2() {
    var _this;
    _classCallCheck(this, TimeSegmentsControls2);
    for (var _len2 = arguments.length, args = new Array(_len2), _key = 0; _key < _len2; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "_onDurationChange", function() {
      _this.updateSegments();
      var _this$player = _this.player, currentTime = _this$player.currentTime, timeSegments = _this$player.timeSegments;
      if (!_this._checkIfEnabled(timeSegments)) {
        return;
      }
      var index = util.getIndexByTime(currentTime, timeSegments);
      var time = util.getOffsetCurrentTime(currentTime, timeSegments, index);
      _this.player.offsetCurrentTime = time;
      _this.changeIndex(index, timeSegments);
    });
    _defineProperty(_assertThisInitialized(_this), "_onLoadedData", function() {
      var timeSegments = _this.player.timeSegments;
      if (!_this._checkIfEnabled(timeSegments)) {
        return;
      }
      var time = util.getOffsetCurrentTime(0, timeSegments);
      _this.player.offsetCurrentTime = time;
      _this.changeIndex(0, timeSegments);
      if (_this.curPos.start > 0) {
        _this.player.currentTime = _this.curPos.start;
      }
    });
    _defineProperty(_assertThisInitialized(_this), "_onTimeupdate", function() {
      var _this$player2 = _this.player, currentTime = _this$player2.currentTime, timeSegments = _this$player2.timeSegments;
      if (!_this._checkIfEnabled(timeSegments)) {
        return;
      }
      var _len = timeSegments.length;
      _this.lastCurrentTime = currentTime;
      var index = util.getIndexByTime(currentTime, timeSegments);
      if (index !== _this.curIndex) {
        _this.changeIndex(index, timeSegments);
      }
      var curTime = util.getOffsetCurrentTime(currentTime, timeSegments, index);
      _this.player.offsetCurrentTime = curTime;
      if (!_this.curPos) {
        return;
      }
      var _this$curPos = _this.curPos, start = _this$curPos.start, end = _this$curPos.end;
      if (currentTime < start) {
        _this.player.currentTime = start;
      } else if (currentTime > end && index >= _len - 1) {
        _this.player.pause();
      }
    });
    _defineProperty(_assertThisInitialized(_this), "_onSeeking", function() {
      var _this$player3 = _this.player, currentTime = _this$player3.currentTime, timeSegments = _this$player3.timeSegments;
      if (!_this._checkIfEnabled(timeSegments)) {
        return;
      }
      if (currentTime < timeSegments[0].start) {
        _this.player.currentTime = timeSegments[0].start;
      } else if (currentTime > timeSegments[timeSegments.length - 1].end) {
        _this.player.currentTime = timeSegments[timeSegments.length - 1].end;
      } else {
        var _index = util.getIndexByTime(currentTime, timeSegments);
        if (_index >= 0) {
          var _seekTime = _this.getSeekTime(currentTime, _this.lastCurrentTime, _index, timeSegments);
          if (_seekTime >= 0) {
            _this.player.currentTime = _seekTime;
          }
        }
      }
    });
    _defineProperty(_assertThisInitialized(_this), "_onPlay", function() {
      var _this$player4 = _this.player, currentTime = _this$player4.currentTime, timeSegments = _this$player4.timeSegments;
      if (_this._checkIfEnabled(timeSegments) && currentTime >= timeSegments[timeSegments.length - 1].end) {
        _this.player.currentTime = timeSegments[0].start;
      }
    });
    return _this;
  }
  _createClass(TimeSegmentsControls2, [{
    key: "afterCreate",
    value: function afterCreate() {
      this.curIndex = -1;
      this.curPos = null;
      this.lastCurrentTime = 0;
      this.updateSegments();
      this.on(DURATION_CHANGE, this._onDurationChange);
      this.on(LOADED_DATA, this._onLoadedData);
      this.on(TIME_UPDATE, this._onTimeupdate);
      this.on(SEEKING, this._onSeeking);
      this.on(PLAY, this._onPlay);
    }
  }, {
    key: "setConfig",
    value: function setConfig(newConfig) {
      var _this2 = this;
      if (!newConfig) {
        return;
      }
      var keys = Object.keys(newConfig);
      if (keys.length < 1) {
        return;
      }
      keys.forEach(function(key) {
        _this2.config[key] = newConfig[key];
      });
      this.updateSegments();
    }
  }, {
    key: "updateSegments",
    value: function updateSegments() {
      var _this$config = this.config, disable = _this$config.disable, segments = _this$config.segments;
      var player = this.player;
      if (disable || !segments || segments.length === 0) {
        player.timeSegments = [];
        player.offsetDuration = 0;
        player.offsetCurrentTime = -1;
      } else {
        var _segs = this.formatTimeSegments(segments, player.duration);
        player.timeSegments = _segs;
        player.offsetDuration = _segs.length > 0 ? _segs[_segs.length - 1].duration : 0;
      }
    }
  }, {
    key: "formatTimeSegments",
    value: function formatTimeSegments(timeSegments, duration) {
      var ret = [];
      if (!timeSegments) {
        return [];
      }
      timeSegments.sort(function(a, b) {
        return a.start - b.start;
      });
      timeSegments.forEach(function(item, index) {
        var _item = {};
        _item.start = item.start < 0 ? 0 : item.start;
        _item.end = duration > 0 && item.end > duration ? duration : item.end;
        if (duration > 0 && _item.start > duration) {
          return;
        }
        ret.push(_item);
        var _segDuration = _item.end - _item.start;
        if (index === 0) {
          _item.offset = item.start;
          _item.cTime = 0;
          _item.segDuration = _segDuration;
          _item.duration = _segDuration;
        } else {
          var last = ret[index - 1];
          _item.offset = last.offset + (_item.start - last.end);
          _item.cTime = last.duration + last.cTime;
          _item.segDuration = _segDuration;
          _item.duration = last.duration + _segDuration;
        }
      });
      return ret;
    }
  }, {
    key: "getSeekTime",
    value: function getSeekTime(currentTime, lastCurrentTime, index, timeSegments) {
      var _time = -1;
      var _timeSegments$index = timeSegments[index], start = _timeSegments$index.start, end = _timeSegments$index.end;
      if (currentTime >= start && currentTime <= end) {
        return _time;
      }
      var diff = currentTime - lastCurrentTime;
      if (diff < 0) {
        if (currentTime < start) {
          var diff2 = lastCurrentTime > start ? lastCurrentTime - start : 0;
          _time = index - 1 >= 0 ? timeSegments[index - 1].end + diff + diff2 : 0;
          return _time;
        }
      }
      return -1;
    }
  }, {
    key: "_checkIfEnabled",
    value: function _checkIfEnabled(segments) {
      return !(!segments || segments.length < 1);
    }
  }, {
    key: "changeIndex",
    value: function changeIndex(index, timeSegments) {
      this.curIndex = index;
      if (index >= 0 && timeSegments.length > 0) {
        this.curPos = timeSegments[index];
      } else {
        this.curPos = null;
      }
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "TimeSegmentsControls";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        disable: true,
        segments: []
      };
    }
  }]);
  return TimeSegmentsControls2;
}(BasePlugin);
export { TimeSegmentsControls as default };
