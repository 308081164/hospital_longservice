import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import "../../utils/debug.js";
import Plugin, { POSITIONS } from "../../plugin/plugin.js";
import PanelIcon from "../assets/panel.js";
var DanmuPanel = /* @__PURE__ */ function(_Plugin) {
  _inherits(DanmuPanel2, _Plugin);
  var _super = _createSuper(DanmuPanel2);
  function DanmuPanel2(args) {
    var _this;
    _classCallCheck(this, DanmuPanel2);
    _this = _super.call(this, args);
    _this.set = {
      speed: 1,
      area: {},
      opacity: 1,
      fonSize: "middle"
    };
    return _this;
  }
  _createClass(DanmuPanel2, [{
    key: "afterCreate",
    value: function afterCreate() {
      if (sniffer.device === "mobile") {
        this.activeEvent = "click";
      } else {
        this.activeEvent = "mouseenter";
      }
      this.onStateChange = this.onStateChange.bind(this);
      this.onToggle = this.onToggle.bind(this);
      this.bind(this.activeEvent, this.onToggle);
      this.bind("mouseleave", this.onToggle);
      this.appendChild(".xgplayer-panel-icon", PanelIcon());
    }
  }, {
    key: "onStateChange",
    value: function onStateChange(e) {
      this.config.onChangeSet && this.config.onChangeSet(this.set);
    }
  }, {
    key: "onToggle",
    value: function onToggle(e) {
      e.preventDefault();
      e.stopPropagation();
      util.hasClass(this.root, "slider-show") ? util.removeClass(this.root, "slider-show") : util.addClass(this.root, "slider-show");
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.unbind(["click", "touchend"], this.onStateChange);
    }
  }, {
    key: "render",
    value: function render() {
      return '\n      <xg-icon class="xgplayer-panel">\n          <xg-panel-icon class="xgplayer-panel-icon">\n          </xg-panel-icon>\n          <xg-panel-slider class="xgplayer-panel-slider">\n            <xg-hidemode class="xgplayer-hidemode">\n              <p class="xgplayer-hidemode-font">\u5C4F\u853D\u7C7B\u578B</p>\n              <ul class="xgplayer-hidemode-radio">\n                <li class="xgplayer-hidemode-scroll" id="false">\u6EDA\u52A8</li><li class="xgplayer-hidemode-top" id="false">\u9876\u90E8</li><li class="xgplayer-hidemode-bottom" id="false">\u5E95\u90E8</li><li class="xgplayer-hidemode-color" id="false">\u8272\u5F69</li>\n              </ul>\n            </xg-hidemode>\n            <xg-transparency class="xgplayer-transparency">\n              <span>\u4E0D\u900F\u660E\u5EA6</span>\n              <input class="xgplayer-transparency-line xgplayer-transparency-color xgplayer-transparency-bar xgplayer-transparency-gradient" type="range" min="0" max="100" step="0.1" value="50"></input>\n            </xg-transparency>\n            <xg-showarea class="xgplayer-showarea">\n              <div class="xgplayer-showarea-name">\u663E\u793A\u533A\u57DF</div>\n              <div class="xgplayer-showarea-control">\n                <div class="xgplayer-showarea-control-up">\n                  <span class="xgplayer-showarea-control-up-item xgplayer-showarea-onequarters">1/4</span>\n                  <span class="xgplayer-showarea-control-up-item xgplayer-showarea-twoquarters selected-color">1/2</span>\n                  <span class="xgplayer-showarea-control-up-item xgplayer-showarea-threequarters">3/4</span>\n                  <span class="xgplayer-showarea-control-up-item xgplayer-showarea-full">1</span>\n                </div>\n                <div class="xgplayer-showarea-control-down">\n                  <div class="xgplayer-showarea-control-down-dots">\n                    <span class="xgplayer-showarea-onequarters-dot"></span>\n                    <span class="xgplayer-showarea-twoquarters-dot"></span>\n                    <span class="xgplayer-showarea-threequarters-dot"></span>\n                    <span class="xgplayer-showarea-full-dot"></span>\n                  </div>\n                  <input class="xgplayer-showarea-line xgplayer-showarea-color xgplayer-showarea-bar xgplayer-gradient" type="range" min="1" max="4" step="1" value="1">\n                </div>\n              </div>\n            </xg-showarea>\n            <xg-danmuspeed class="xgplayer-danmuspeed">\n              <div class="xgplayer-danmuspeed-name">\u5F39\u5E55\u901F\u5EA6</div>\n              <div class="xgplayer-danmuspeed-control">\n                <div class="xgplayer-danmuspeed-control-up">\n                  <span class="xgplayer-danmuspeed-control-up-item xgplayer-danmuspeed-small">\u6162</span>\n                  <span class="xgplayer-danmuspeed-control-up-item xgplayer-danmuspeed-middle selected-color">\u4E2D</span>\n                  <span class="xgplayer-danmuspeed-control-up-item xgplayer-danmuspeed-large">\u5FEB</span>\n                </div>\n                <div class="xgplayer-danmuspeed-control-down">\n                  <div class="xgplayer-danmuspeed-control-down-dots">\n                    <span class="xgplayer-danmuspeed-small-dot"></span>\n                    <span class="xgplayer-danmuspeed-middle-dot"></span>\n                    <span class="xgplayer-danmuspeed-large-dot"></span>\n                  </div>\n                  <input class="xgplayer-danmuspeed-line xgplayer-danmuspeed-color xgplayer-danmuspeed-bar xgplayer-gradient" type="range" min="50" max="150" step="50" value="100">\n                </div>\n              </div>\n            </xg-danmuspeed>\n            <xg-danmufont class="xgplayer-danmufont">\n              <div class="xgplayer-danmufont-name">\u5B57\u4F53\u5927\u5C0F</div>\n              <div class="xgplayer-danmufont-control">\n                <div class="xgplayer-danmufont-control-up">\n                  <span class="xgplayer-danmufont-control-up-item xgplayer-danmufont-small">\u5C0F</span>\n                  <span class="xgplayer-danmufont-control-up-item xgplayer-danmufont-middle">\u4E2D</span>\n                  <span class="xgplayer-danmufont-control-up-item xgplayer-danmufont-large selected-color">\u5927</span>\n                </div>\n                <div class="xgplayer-danmufont-control-down">\n                  <div class="xgplayer-danmufont-control-down-dots">\n                    <span class="xgplayer-danmufont-small-dot"></span>\n                    <span class="xgplayer-danmufont-middle-dot"></span>\n                    <span class="xgplayer-danmufont-large-dot"></span>\n                  </div>\n                  <input class="xgplayer-danmufont-line xgplayer-danmufont-color xgplayer-danmufont-bar xgplayer-gradient" type="range" min="20" max="30" step="5" value="25">\n                </div>\n              </div>\n            </xg-danmufont>\n          </xg-panel-slider>\n      </xg-icon>';
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "danmuPanel";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 11,
        onChangeSet: function onChangeSet(set) {
          console.log("DanmuPanel:".concat(set));
        },
        speed: 1,
        area: {},
        opacity: 1,
        fonSize: "middle"
      };
    }
  }]);
  return DanmuPanel2;
}(Plugin);
export { DanmuPanel as default };
