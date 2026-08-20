import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import { FULLSCREEN_CHANGE } from "../../events.js";
import "../../utils/debug.js";
import { POSITIONS } from "../../plugin/plugin.js";
import { xgIconTips } from "../common/iconTools.js";
import Icon from "../common/iconPlugin.js";
import TopBackIcon from "./backicon.js";
import FullScreenSvg from "../assets/requestFull.js";
import ExitFullScreenSvg from "../assets/exitFull.js";
var Fullscreen = /* @__PURE__ */ function(_IconPlugin) {
  _inherits(Fullscreen2, _IconPlugin);
  var _super = _createSuper(Fullscreen2);
  function Fullscreen2() {
    var _this;
    _classCallCheck(this, Fullscreen2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "_onOrientationChange", function(e) {
      if (_this.player.fullscreen && _this.config.rotateFullscreen) {
        if (window.orientation === 90 || window.orientation === -90) {
          _this.player.setRotateDeg(0);
        } else {
          _this.player.setRotateDeg(90);
        }
      }
    });
    return _this;
  }
  _createClass(Fullscreen2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      _get(_getPrototypeOf(Fullscreen2.prototype), "afterCreate", this).call(this);
      var config = this.config, playerConfig = this.playerConfig;
      if (config.disable) {
        return;
      }
      if (config.target) {
        this.playerConfig.fullscreenTarget = this.config.target;
      }
      var fullEl = util.getFullScreenEl();
      if (playerConfig.fullscreenTarget === fullEl) {
        this.player.getFullscreen().catch(function(e) {
        });
      }
      this.initIcons();
      this.handleFullscreen = this.hook("fullscreenChange", this.toggleFullScreen, {
        pre: function pre(e) {
          var fullscreen = _this2.player.fullscreen;
          _this2.emitUserAction(e, "switch_fullscreen", {
            prop: "fullscreen",
            from: fullscreen,
            to: !fullscreen
          });
        }
      });
      this.bind(".xgplayer-fullscreen", ["touchend", "click"], this.handleFullscreen);
      this.on(FULLSCREEN_CHANGE, function(isFullScreen) {
        var tipsDom = _this2.find(".xg-tips");
        tipsDom && _this2.changeLangTextKey(tipsDom, isFullScreen ? _this2.i18nKeys.EXITFULLSCREEN_TIPS : _this2.i18nKeys.FULLSCREEN_TIPS);
        _this2.animate(isFullScreen);
      });
      if (this.config.needBackIcon) {
        this.topBackIcon = this.player.registerPlugin({
          plugin: TopBackIcon,
          options: {
            config: {
              onClick: function onClick(e) {
                _this2.handleFullscreen(e);
              }
            }
          }
        });
      }
      if (sniffer.device === "mobile") {
        window.addEventListener("orientationchange", this._onOrientationChange);
      }
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        fullscreen: {
          icon: FullScreenSvg,
          class: "xg-get-fullscreen"
        },
        exitFullscreen: {
          icon: ExitFullScreenSvg,
          class: "xg-exit-fullscreen"
        }
      };
    }
  }, {
    key: "destroy",
    value: function destroy() {
      _get(_getPrototypeOf(Fullscreen2.prototype), "destroy", this).call(this);
      this.unbind(".xgplayer-icon", sniffer.device === "mobile" ? "touchend" : "click", this.handleFullscreen);
      if (sniffer.device === "mobile") {
        window.removeEventListener("orientationchange", this._onOrientationChange);
      }
    }
  }, {
    key: "initIcons",
    value: function initIcons() {
      var icons = this.icons;
      this.appendChild(".xgplayer-icon", icons.fullscreen);
      this.appendChild(".xgplayer-icon", icons.exitFullscreen);
    }
  }, {
    key: "toggleFullScreen",
    value: function toggleFullScreen(e) {
      if (e instanceof Event) {
        e.preventDefault();
        e.stopPropagation();
      }
      var player = this.player, config = this.config;
      var useCssFullscreen = config.useCssFullscreen === true || typeof config.useCssFullscreen === "function" && config.useCssFullscreen();
      if (useCssFullscreen) {
        if (player.fullscreen) {
          player.exitCssFullscreen();
        } else {
          player.getCssFullscreen();
        }
        this.animate(player.fullscreen);
      } else if (config.rotateFullscreen) {
        if (player.fullscreen) {
          player.exitRotateFullscreen();
        } else {
          player.getRotateFullscreen();
        }
        this.animate(player.fullscreen);
      } else if (config.switchCallback && typeof config.switchCallback === "function") {
        config.switchCallback(player.fullscreen);
      } else {
        if (player.fullscreen) {
          player.exitFullscreen();
          if (config.useScreenOrientation) {
            this.unlockScreen();
          }
        } else {
          player.getFullscreen().catch(function(e2) {
          });
          if (config.useScreenOrientation && player.aspectRatio > 1) {
            this.lockScreen(config.lockOrientationType);
          }
        }
      }
    }
  }, {
    key: "animate",
    value: function animate(isFullScreen) {
      isFullScreen ? this.setAttr("data-state", "full") : this.setAttr("data-state", "normal");
      if (this.topBackIcon) {
        if (isFullScreen) {
          this.topBackIcon.show();
          this.hide();
        } else {
          this.topBackIcon.hide();
          this.show();
        }
      }
    }
  }, {
    key: "render",
    value: function render() {
      if (this.config.disable) {
        return;
      }
      var langKey = "FULLSCREEN_TIPS";
      return '<xg-icon class="xgplayer-fullscreen">\n    <div class="xgplayer-icon">\n    </div>\n    '.concat(xgIconTips(this, langKey, this.playerConfig.isHideTips), "\n    </xg-icon>");
    }
  }, {
    key: "lockScreen",
    value: function lockScreen(orientation) {
      try {
        screen.orientation.lock(orientation).catch(function(e) {
        });
      } catch (e) {
      }
    }
  }, {
    key: "unlockScreen",
    value: function unlockScreen() {
      try {
        screen.orientation.unlock().catch(function(e) {
        });
      } catch (e) {
      }
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "fullscreen";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 0,
        useCssFullscreen: false,
        rotateFullscreen: false,
        useScreenOrientation: false,
        lockOrientationType: "landscape",
        switchCallback: null,
        target: null,
        disable: false,
        needBackIcon: false
      };
    }
  }]);
  return Fullscreen2;
}(Icon);
export { Fullscreen as default };
