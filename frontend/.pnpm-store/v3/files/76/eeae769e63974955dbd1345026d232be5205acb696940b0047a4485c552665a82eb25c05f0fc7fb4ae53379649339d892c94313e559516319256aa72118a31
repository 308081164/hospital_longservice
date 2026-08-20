import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { ENDED, PLAY } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
import ReplaySvg from "../assets/replay.js";
var Replay = /* @__PURE__ */ function(_Plugin) {
  _inherits(Replay2, _Plugin);
  var _super = _createSuper(Replay2);
  function Replay2() {
    _classCallCheck(this, Replay2);
    return _super.apply(this, arguments);
  }
  _createClass(Replay2, [{
    key: "registerIcons",
    value: function registerIcons() {
      return {
        replay: ReplaySvg
      };
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this = this;
      Plugin.insert(this.icons.replay, this.root, 0);
      this.__handleReplay = this.hook("replayClick", function() {
        _this.player.replay();
      }, {
        pre: function pre(e) {
          e.preventDefault();
          e.stopPropagation();
        }
      });
      this.bind(".xgplayer-replay", ["click", "touchend"], this.__handleReplay);
      this.on(ENDED, function() {
        if (!_this.playerConfig.loop) {
          util.addClass(_this.player.root, "replay");
        }
        if (_this.config.disable) {
          return;
        }
        _this.show();
        var path = _this.root.querySelector("path");
        if (path) {
          var transform = window.getComputedStyle(path).getPropertyValue("transform");
          if (typeof transform === "string" && transform.indexOf("none") > -1) {
            return null;
          } else {
            path.setAttribute("transform", transform);
          }
        }
      });
      this.on(PLAY, function() {
        _this.hide();
      });
    }
  }, {
    key: "handleReplay",
    value: function handleReplay(e) {
      e.preventDefault();
      e.stopPropagation();
      this.player.replay();
      util.removeClass(this.player.root, "replay");
    }
  }, {
    key: "show",
    value: function show(value) {
      if (this.config.disable) {
        return;
      }
      this.root.style.display = "flex";
    }
  }, {
    key: "enable",
    value: function enable() {
      this.config.disable = false;
    }
  }, {
    key: "disable",
    value: function disable() {
      this.config.disable = true;
      this.hide();
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.unbind(".xgplayer-replay", ["click", "touchend"], this.__handleReplay);
    }
  }, {
    key: "render",
    value: function render() {
      return '<xg-replay class="xgplayer-replay">\n      <xg-replay-txt class="xgplayer-replay-txt" lang-key="'.concat(this.i18nKeys.REPLAY, '">').concat(this.i18n.REPLAY, "</xg-replay-txt>\n    </xg-replay>");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "replay";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        disable: false
      };
    }
  }]);
  return Replay2;
}(Plugin);
export { Replay as default };
