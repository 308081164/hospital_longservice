import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { TIME_UPDATE, EMPTIED } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
function getBgColor(color) {
  return color ? "background:".concat(color, ";") : "";
}
var MiniProgress = /* @__PURE__ */ function(_Plugin) {
  _inherits(MiniProgress2, _Plugin);
  var _super = _createSuper(MiniProgress2);
  function MiniProgress2() {
    var _this;
    _classCallCheck(this, MiniProgress2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "onTimeupdate", function() {
      var ended = _this.player.ended;
      var _assertThisInitialize = _assertThisInitialized(_this), offsetDuration = _assertThisInitialize.offsetDuration;
      var time = _this.currentTime;
      time = util.adjustTimeByDuration(time, offsetDuration, ended);
      _this.update({
        played: time
      }, offsetDuration);
    });
    return _this;
  }
  _createClass(MiniProgress2, [{
    key: "offsetDuration",
    get: function get() {
      return this.playerConfig.customDuration || this.player.offsetDuration || this.player.duration;
    }
  }, {
    key: "currentTime",
    get: function get() {
      var _this$player = this.player, offsetCurrentTime = _this$player.offsetCurrentTime, currentTime = _this$player.currentTime;
      return offsetCurrentTime >= 0 ? offsetCurrentTime : currentTime;
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      if (!this.root) {
        return;
      }
      this.on(TIME_UPDATE, this.onTimeupdate);
      this.on(EMPTIED, function() {
        _this2.reset();
      });
    }
  }, {
    key: "reset",
    value: function reset() {
      this.update({
        played: 0,
        cached: 0
      }, 0);
    }
  }, {
    key: "update",
    value: function update() {
      var data = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {
        cached: 0,
        played: 0
      };
      var duration = arguments.length > 1 ? arguments[1] : void 0;
      if (!duration || !this.root) {
        return;
      }
      if (data.cached) {
        this.find("xg-mini-progress-cache").style.width = "".concat(data.cached / duration * 100, "%");
      }
      if (data.played) {
        this.find("xg-mini-progress-played").style.width = "".concat(data.played / duration * 100, "%");
      }
    }
  }, {
    key: "render",
    value: function render() {
      var _this$playerConfig = this.playerConfig, commonStyle = _this$playerConfig.commonStyle, miniprogress = _this$playerConfig.miniprogress;
      if (!miniprogress) {
        return;
      }
      var _this$config = this.config, mode = _this$config.mode, height = _this$config.height;
      var _style = {
        cached: getBgColor(commonStyle.cachedColor),
        played: getBgColor(commonStyle.playedColor),
        progress: getBgColor(commonStyle.progressColor),
        height: height > 0 && height !== 2 ? "height: ".concat(height, "px;") : ""
      };
      var stateClass = mode === "show" ? "xg-mini-progress-show" : "";
      return '<xg-mini-progress class="xg-mini-progress '.concat(stateClass, '" style="').concat(_style.progress, " ").concat(_style.height, '">\n    <xg-mini-progress-cache class="xg-mini-progress-cache" style="').concat(_style.cached, '"></xg-mini-progress-cache>\n    <xg-mini-progress-played class="xg-mini-progress-played" style="').concat(_style.played, '"></xg-mini-progress-played>\n    </xg-mini-progress>');
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "MiniProgress";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        mode: "auto",
        height: 2
      };
    }
  }]);
  return MiniProgress2;
}(Plugin);
export { MiniProgress as default };
