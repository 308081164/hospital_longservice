import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import { WAITING, PLAYING, CANPLAY, PLAY } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var WaitingTimeoutJump = /* @__PURE__ */ function(_BasePlugin) {
  _inherits(WaitingTimeoutJump2, _BasePlugin);
  var _super = _createSuper(WaitingTimeoutJump2);
  function WaitingTimeoutJump2() {
    var _this;
    _classCallCheck(this, WaitingTimeoutJump2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "onWaiting", function() {
      var _assertThisInitialize = _assertThisInitialized(_this), config = _assertThisInitialize.config;
      if (_this.jumpCnt > config.jumpCntMax || _this.timer || config.useWaitingTimeoutJump === false) {
        return;
      }
      _this.timer = setTimeout(_this.onJump, config.waitingTime * 1e3);
    });
    _defineProperty(_assertThisInitialized(_this), "onJump", function() {
      var _assertThisInitialize2 = _assertThisInitialized(_this), player = _assertThisInitialize2.player, config = _assertThisInitialize2.config;
      clearTimeout(_this.timer);
      _this.timer = null;
      if (_this.jumpCnt > config.jumpCntMax || config.useWaitingTimeoutJump === false) {
        return;
      }
      if (player.media.paused && player.media.currentTime !== 0 && _this.hasPlayed) {
        return;
      }
      _this.jumpSize = config.jumpSize * (_this.jumpCnt + 1);
      if (_this.jumpCnt === config.jumpSize && _this.jumpSize < 6) {
        _this.jumpSize = 6;
      }
      var jumpTo = player.currentTime + _this.jumpSize;
      var seekEnd = player.media.duration;
      if (jumpTo > seekEnd) {
        return;
      }
      _this.jumpCnt++;
      player.currentTime = jumpTo;
    });
    return _this;
  }
  _createClass(WaitingTimeoutJump2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      var _this$config = this.config, useWaitingTimeoutJump = _this$config.useWaitingTimeoutJump, jumpSize = _this$config.jumpSize;
      if (useWaitingTimeoutJump === false) {
        return;
      }
      this.hasPlayed = false;
      this.jumpCnt = 0;
      this.timer = null;
      this.jumpSize = jumpSize;
      this.on(WAITING, this.onWaiting);
      this.on([PLAYING, CANPLAY], function() {
        clearTimeout(_this2.timer);
        _this2.timer = null;
        _this2.jumpSize = _this2.config.jumpSize;
      });
      this.on(PLAY, function() {
        _this2.hasPlayed = true;
      });
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "waitingTimeoutJump";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        useWaitingTimeoutJump: false,
        waitingTime: 15,
        jumpSize: 2,
        jumpCntMax: 4
      };
    }
  }]);
  return WaitingTimeoutJump2;
}(Plugin);
export { WaitingTimeoutJump as default };
