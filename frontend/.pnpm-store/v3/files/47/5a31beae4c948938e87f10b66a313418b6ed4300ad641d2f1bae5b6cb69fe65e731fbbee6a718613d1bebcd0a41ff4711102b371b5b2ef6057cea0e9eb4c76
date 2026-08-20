import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import { PLAY, PAUSE, ERROR, EMPTIED } from "../../events.js";
import "../../utils/debug.js";
import { POSITIONS } from "../../plugin/plugin.js";
import { xgIconTips } from "../common/iconTools.js";
import Icon from "../common/iconPlugin.js";
import PlayIcon from "../assets/play.js";
import PauseIcon from "../assets/pause.js";
var Play = /* @__PURE__ */ function(_IconPlugin) {
  _inherits(Play2, _IconPlugin);
  var _super = _createSuper(Play2);
  function Play2() {
    var _this;
    _classCallCheck(this, Play2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "btnClick", function(e) {
      e.preventDefault();
      e.stopPropagation();
      var _assertThisInitialize = _assertThisInitialized(_this), player = _assertThisInitialize.player;
      _this.emitUserAction(e, "switch_play_pause", {
        prop: "paused",
        from: player.paused,
        to: !player.paused
      });
      if (player.ended) {
        player.replay();
      } else if (player.paused) {
        player.play();
        _this.animate(false);
      } else {
        player.pause();
        _this.animate(true);
      }
      return false;
    });
    return _this;
  }
  _createClass(Play2, [{
    key: "afterCreate",
    value: function afterCreate() {
      _get(_getPrototypeOf(Play2.prototype), "afterCreate", this).call(this);
      var config = this.config;
      if (config.disable) {
        return;
      }
      this.initIcons();
      this.bind(["touchend", "click"], this.btnClick);
      this.listenEvents();
      this.animate(true);
    }
  }, {
    key: "listenEvents",
    value: function listenEvents() {
      var _this2 = this;
      var player = this.player;
      this.on([PLAY, PAUSE, ERROR, EMPTIED], function() {
        _this2.animate(player.paused);
      });
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
      this.appendChild(".xgplayer-icon", icons.play);
      this.appendChild(".xgplayer-icon", icons.pause);
    }
  }, {
    key: "animate",
    value: function animate(paused) {
      if (!this.player) {
        return;
      }
      var i18nKeys = this.i18nKeys;
      var tipDom = this.find(".xg-tips");
      if (paused) {
        this.setAttr("data-state", "pause");
        tipDom && this.changeLangTextKey(tipDom, i18nKeys.PLAY_TIPS);
      } else {
        this.setAttr("data-state", "play");
        tipDom && this.changeLangTextKey(tipDom, i18nKeys.PAUSE_TIPS);
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      _get(_getPrototypeOf(Play2.prototype), "destroy", this).call(this);
      this.unbind(["touchend", "click"], this.btnClick);
    }
  }, {
    key: "render",
    value: function render() {
      if (this.config.disable) {
        return;
      }
      return '<xg-icon class="xgplayer-play">\n    <div class="xgplayer-icon">\n    </div>\n    '.concat(xgIconTips(this, "PLAY_TIPS", this.playerConfig.isHideTips), "\n    </xg-icon>");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "play";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_LEFT,
        index: 0,
        disable: false
      };
    }
  }]);
  return Play2;
}(Icon);
export { Play as default };
