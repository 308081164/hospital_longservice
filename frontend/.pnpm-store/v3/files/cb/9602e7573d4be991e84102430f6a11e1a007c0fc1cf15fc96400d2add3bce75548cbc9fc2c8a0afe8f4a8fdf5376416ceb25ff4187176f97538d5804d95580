import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { PAUSE, PLAY, MINI_STATE_CHANGE } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
import { xgIconTips } from "../common/iconTools.js";
import PlayIcon from "../assets/play.js";
import PauseIcon from "../assets/pause.js";
import MiniScreenIcon from "./miniScreenIcon.js";
export { default as MiniScreenIcon } from "./miniScreenIcon.js";
import Draggabilly from "../../utils/draggabilly.js";
var MiniScreen = /* @__PURE__ */ function(_Plugin) {
  _inherits(MiniScreen2, _Plugin);
  var _super = _createSuper(MiniScreen2);
  function MiniScreen2(args) {
    var _this;
    _classCallCheck(this, MiniScreen2);
    _this = _super.call(this, args);
    _defineProperty(_assertThisInitialized(_this), "onCancelClick", function(e) {
      _this.exitMini();
      _this.isClose = true;
    });
    _defineProperty(_assertThisInitialized(_this), "onCenterClick", function(e) {
      var _assertThisInitialize = _assertThisInitialized(_this), player = _assertThisInitialize.player;
      player.paused ? player.play() : player.pause();
    });
    _defineProperty(_assertThisInitialized(_this), "onScroll", function(e) {
      if (!window.scrollY && window.scrollY !== 0 || Math.abs(window.scrollY - _this.pos.scrollY) < 50) {
        return;
      }
      var scrollHeight = parseInt(util.getCss(_this.player.root, "height"));
      scrollHeight += _this.config.scrollTop;
      _this.pos.scrollY = window.scrollY;
      if (window.scrollY > scrollHeight + 5) {
        !_this.isMini && !_this.isClose && _this.getMini();
      } else if (window.scrollY <= scrollHeight) {
        _this.isMini && _this.exitMini();
        _this.isClose = false;
      }
    });
    _this.isMini = false;
    _this.isClose = false;
    var _assertThisInitialize2 = _assertThisInitialized(_this), config = _assertThisInitialize2.config;
    _this.pos = {
      left: config.left < 0 ? window.innerWidth - config.width - 20 : config.left,
      top: config.top < 0 ? window.innerHeight - config.height - 20 : config.top,
      height: _this.config.height,
      width: _this.config.width,
      scrollY: window.scrollY || 0
    };
    _this.lastStyle = null;
    return _this;
  }
  _createClass(MiniScreen2, [{
    key: "beforeCreate",
    value: function beforeCreate(args) {
      if (typeof args.player.config.mini === "boolean") {
        args.config.isShowIcon = args.player.config.mini;
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      this.initIcons();
      this.on(PAUSE, function() {
        _this2.setAttr("data-state", "pause");
      });
      this.on(PLAY, function() {
        _this2.setAttr("data-state", "play");
      });
    }
  }, {
    key: "onPluginsReady",
    value: function onPluginsReady() {
      var _this3 = this;
      var player = this.player, config = this.config;
      if (config.disable) {
        return;
      }
      if (this.config.isShowIcon) {
        var options = {
          config: {
            onClick: function onClick() {
              _this3.getMini();
            }
          }
        };
        player.controls.registerPlugin(MiniScreenIcon, options, MiniScreenIcon.pluginName);
      }
      var eventName = util.checkTouchSupport() ? "touchend" : "click";
      this.bind(".mini-cancel-btn", eventName, this.onCancelClick);
      this.bind(".play-icon", eventName, this.onCenterClick);
      if (!this.config.disableDrag) {
        this._draggabilly = new Draggabilly(this.player.root, {
          handle: this.root
        });
      }
      if (this.config.isScrollSwitch) {
        window.addEventListener("scroll", this.onScroll);
      }
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        play: {
          icon: PlayIcon,
          class: "xg-icon-play"
        },
        pause: {
          icon: PauseIcon,
          class: "xg-icon-pause"
        }
      };
    }
  }, {
    key: "initIcons",
    value: function initIcons() {
      var icons = this.icons;
      this.appendChild(".play-icon", icons.play);
      this.appendChild(".play-icon", icons.pause);
    }
  }, {
    key: "getMini",
    value: function getMini() {
      var _this4 = this;
      if (this.isMini) {
        return;
      }
      var player = this.player, playerConfig = this.playerConfig;
      var target = this.config.target || this.player.root;
      this.lastStyle = {};
      util.addClass(player.root, "xgplayer-mini");
      ["width", "height", "top", "left"].map(function(key) {
        _this4.lastStyle[key] = target.style[key];
        target.style[key] = "".concat(_this4.pos[key], "px");
      });
      if (playerConfig.fluid) {
        target.style["padding-top"] = "";
      }
      this.emit(MINI_STATE_CHANGE, true);
      player.isMini = this.isMini = true;
    }
  }, {
    key: "exitMini",
    value: function exitMini() {
      var _this5 = this;
      if (!this.isMini) {
        return false;
      }
      var player = this.player, playerConfig = this.playerConfig;
      var target = this.config.target || this.player.root;
      util.removeClass(player.root, "xgplayer-mini");
      if (this.lastStyle) {
        Object.keys(this.lastStyle).map(function(key) {
          target.style[key] = _this5.lastStyle[key];
        });
      }
      this.lastStyle = null;
      if (playerConfig.fluid) {
        player.root.style.width = "100%";
        player.root.style.height = "0";
        player.root.style["padding-top"] = "".concat(playerConfig.height * 100 / playerConfig.width, "%");
      }
      this.emit(MINI_STATE_CHANGE, false);
      this.isMini = player.isMini = false;
    }
  }, {
    key: "updatePos",
    value: function updatePos(pos) {
      this.pos = Object.assign(this.pos, pos);
      if (this.isMini) {
        this.player.root.style.left = "".concat(this.pos.left, "px");
        this.player.root.style.top = "".concat(this.pos.top, "px");
        this.player.root.style.width = "".concat(this.pos.width, "px");
        this.player.root.style.height = "".concat(this.pos.height, "px");
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      window.removeEventListener("scroll", this.onScroll);
      var eventName = util.checkTouchSupport() ? "touchend" : "click";
      this.unbind(".mini-cancel-btn", eventName, this.onCancelClick);
      this.unbind(".play-icon", eventName, this.onCenterClick);
      this._draggabilly && this._draggabilly.destroy();
      this._draggabilly = null;
      this.exitMini();
    }
  }, {
    key: "render",
    value: function render() {
      if (this.config.disable) {
        return;
      }
      return '\n      <xg-mini-layer class="xg-mini-layer">\n      <xg-mini-header class="xgplayer-mini-header">\n      '.concat(xgIconTips(this, "MINI_DRAG", this.playerConfig.isHideTips), '\n      </xg-mini-header>\n      <div class="mini-cancel-btn">\n        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20">\n          <path fill="#fff" fill-rule="evenodd" d="M3.99 3.49a1 1 0 0 1 1.414 0L10 8.085l4.596-4.595a1 1 0 1 1 1.414 1.414L11.414 9.5l4.596 4.596a1 1 0 0 1 .084 1.32l-.084.094a1 1 0 0 1-1.414 0L10 10.914 5.404 15.51a1 1 0 0 1-1.414-1.414L8.585 9.5 3.99 4.904a1 1 0 0 1-.084-1.32z"></path>\n        </svg>\n      </div>\n      <div class="play-icon">\n      </div>\n      </xg-mini-layer>');
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "miniscreen";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        index: 10,
        disable: false,
        width: 320,
        height: 180,
        left: -1,
        top: -1,
        isShowIcon: false,
        isScrollSwitch: false,
        scrollTop: 0,
        disableDrag: false
      };
    }
  }]);
  return MiniScreen2;
}(Plugin);
export { MiniScreen as default };
