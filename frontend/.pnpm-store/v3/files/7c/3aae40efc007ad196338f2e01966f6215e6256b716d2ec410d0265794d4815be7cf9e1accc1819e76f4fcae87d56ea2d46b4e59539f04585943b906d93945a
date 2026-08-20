import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import "../../utils/debug.js";
import Plugin, { POSITIONS } from "../../plugin/plugin.js";
var MiniScreenIcon = /* @__PURE__ */ function(_Plugin) {
  _inherits(MiniScreenIcon2, _Plugin);
  var _super = _createSuper(MiniScreenIcon2);
  function MiniScreenIcon2() {
    _classCallCheck(this, MiniScreenIcon2);
    return _super.apply(this, arguments);
  }
  _createClass(MiniScreenIcon2, [{
    key: "afterCreate",
    value: function afterCreate() {
      this.getMini = this.getMini.bind(this);
      this.exitMini = this.exitMini.bind(this);
      this.bind("click", this.getMini);
    }
  }, {
    key: "getMini",
    value: function getMini() {
      this.config.onClick && this.config.onClick();
    }
  }, {
    key: "exitMini",
    value: function exitMini() {
      this.config.onClick && this.config.onClick();
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.unbind(["click", "touchend"], this.getMini);
    }
  }, {
    key: "render",
    value: function render() {
      var langKey = "MINISCREEN";
      return '\n      <xg-icon class="xgplayer-miniicon">\n      <div class="xgplayer-icon btn-text"><span class="icon-text" lang-key="'.concat(this.i18nKeys[langKey], '">').concat(this.i18n[langKey], "</span></div>\n      </xg-icon>");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "miniscreenIcon";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 10
      };
    }
  }]);
  return MiniScreenIcon2;
}(Plugin);
export { MiniScreenIcon as default };
