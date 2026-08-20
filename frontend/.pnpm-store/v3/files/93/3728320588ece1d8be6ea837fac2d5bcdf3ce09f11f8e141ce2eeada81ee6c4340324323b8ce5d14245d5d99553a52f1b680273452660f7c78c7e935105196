import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import { CSS_FULLSCREEN_CHANGE } from "../../events.js";
import "../../utils/debug.js";
import { POSITIONS } from "../../plugin/plugin.js";
import { xgIconTips } from "../common/iconTools.js";
import Icon from "../common/iconPlugin.js";
import CssFullSceenSvg from "../assets/requestCssFull.js";
import ExitCssFullSceenSvg from "../assets/exitCssFull.js";
var CssFullScreen = /* @__PURE__ */ function(_IconPlugin) {
  _inherits(CssFullScreen2, _IconPlugin);
  var _super = _createSuper(CssFullScreen2);
  function CssFullScreen2() {
    _classCallCheck(this, CssFullScreen2);
    return _super.apply(this, arguments);
  }
  _createClass(CssFullScreen2, [{
    key: "beforeCreate",
    value: function beforeCreate(args) {
      if (typeof args.player.config.cssFullscreen === "boolean") {
        args.config.disable = !args.player.config.cssFullscreen;
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this = this;
      _get(_getPrototypeOf(CssFullScreen2.prototype), "afterCreate", this).call(this);
      if (this.config.disable) {
        return;
      }
      if (this.config.target) {
        this.playerConfig.fullscreenTarget = this.config.target;
      }
      this.initIcons();
      this.on(CSS_FULLSCREEN_CHANGE, function(isCssfullScreen) {
        _this.animate(isCssfullScreen);
      });
      this.btnClick = this.btnClick.bind(this);
      this.handleCssFullscreen = this.hook("cssFullscreen_change", this.btnClick, {
        pre: function pre(e) {
          e.preventDefault();
          e.stopPropagation();
        }
      });
      this.bind(["click", "touchend"], this.handleCssFullscreen);
    }
  }, {
    key: "initIcons",
    value: function initIcons() {
      var icons = this.icons;
      var contentIcon = this.find(".xgplayer-icon");
      contentIcon.appendChild(icons.cssFullscreen);
      contentIcon.appendChild(icons.exitCssFullscreen);
    }
  }, {
    key: "btnClick",
    value: function btnClick(e) {
      e.preventDefault();
      e.stopPropagation();
      var isCssfullScreen = this.player.isCssfullScreen;
      this.emitUserAction(e, "switch_cssfullscreen", {
        cssfullscreen: isCssfullScreen
      });
      if (!isCssfullScreen) {
        this.player.getCssFullscreen();
      } else {
        this.player.exitCssFullscreen();
      }
    }
  }, {
    key: "animate",
    value: function animate(isFullScreen) {
      if (!this.root) {
        return;
      }
      isFullScreen ? this.setAttr("data-state", "full") : this.setAttr("data-state", "normal");
      this.switchTips(isFullScreen);
    }
  }, {
    key: "switchTips",
    value: function switchTips(isFullScreen) {
      var i18nKeys = this.i18nKeys;
      var tipDom = this.find(".xg-tips");
      tipDom && this.changeLangTextKey(tipDom, isFullScreen ? i18nKeys.EXITCSSFULLSCREEN_TIPS : i18nKeys.CSSFULLSCREEN_TIPS);
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        cssFullscreen: {
          icon: CssFullSceenSvg,
          class: "xg-get-cssfull"
        },
        exitCssFullscreen: {
          icon: ExitCssFullSceenSvg,
          class: "xg-exit-cssfull"
        }
      };
    }
  }, {
    key: "destroy",
    value: function destroy() {
      _get(_getPrototypeOf(CssFullScreen2.prototype), "destroy", this).call(this);
      this.unbind(["click", "touchend"], this.btnClick);
    }
  }, {
    key: "render",
    value: function render() {
      if (this.config.disable) {
        return;
      }
      return `<xg-icon class='xgplayer-cssfullscreen'>
    <div class="xgplayer-icon">
    </div>
    `.concat(xgIconTips(this, "CSSFULLSCREEN_TIPS", this.playerConfig.isHideTips), "\n    </xg-icon>");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "cssFullscreen";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 1,
        disable: false,
        target: null
      };
    }
  }]);
  return CssFullScreen2;
}(Icon);
export { CssFullScreen as default };
