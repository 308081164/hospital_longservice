import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import { PLAYNEXT } from "../../events.js";
import "../../utils/debug.js";
import Plugin, { POSITIONS } from "../../plugin/plugin.js";
import { xgIconTips } from "../common/iconTools.js";
import Next from "../assets/playNext.js";
var PlayNextIcon = /* @__PURE__ */ function(_Plugin) {
  _inherits(PlayNextIcon2, _Plugin);
  var _super = _createSuper(PlayNextIcon2);
  function PlayNextIcon2(options) {
    var _this;
    _classCallCheck(this, PlayNextIcon2);
    _this = _super.call(this, options);
    _defineProperty(_assertThisInitialized(_this), "playNext", function(e) {
      var _assertThisInitialize = _assertThisInitialized(_this), player = _assertThisInitialize.player;
      e.preventDefault();
      e.stopPropagation();
      if (_this.idx + 1 < _this.config.urlList.length) {
        _this.idx++;
        _this.nextHandler(_this.config.urlList[_this.idx], _this.idx);
        player.emit(PLAYNEXT, _this.idx + 1);
      } else {
        _this.nextHandler();
        player.emit(PLAYNEXT);
      }
    });
    _this.idx = -1;
    return _this;
  }
  _createClass(PlayNextIcon2, [{
    key: "afterCreate",
    value: function afterCreate() {
      if (!this.config.urlList || this.config.urlList.length === 0) {
        return;
      }
      this.appendChild(".xgplayer-icon", this.icons.playNext);
      this.initEvents();
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        playNext: Next
      };
    }
  }, {
    key: "initEvents",
    value: function initEvents() {
      this.nextHandler = this.hook("nextClick", this.changeSrc);
      var event = sniffer.device === "mobile" ? "touchend" : "click";
      this.bind(event, this.playNext);
      this.show();
    }
  }, {
    key: "changeSrc",
    value: function changeSrc(url) {
      var player = this.player;
      if (!url) {
        return;
      }
      player.pause();
      player.currentTime = 0;
      if (player.switchURL) {
        player.switchURL(url);
      } else {
        player.src = url;
      }
      player.config.url = url;
      player.play();
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.unbind(["touchend", "click"], this.playNext);
    }
  }, {
    key: "render",
    value: function render() {
      if (!this.config.urlList || this.config.urlList.length === 0) {
        return;
      }
      return '\n     <xg-icon class="xgplayer-playnext">\n      <div class="xgplayer-icon">\n      </div>\n      '.concat(xgIconTips(this, "PLAYNEXT_TIPS", this.playerConfig.isHideTips), "\n     </xg-icon>\n    ");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "playNext";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_LEFT,
        index: 1,
        url: null,
        urlList: []
      };
    }
  }]);
  return PlayNextIcon2;
}(Plugin);
export { PlayNextIcon as default };
