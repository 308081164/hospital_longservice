import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import "../../utils/debug.js";
import Plugin, { POSITIONS } from "../../plugin/plugin.js";
import BackSVG from "../assets/back.js";
var TopBackIcon = /* @__PURE__ */ function(_Plugin) {
  _inherits(TopBackIcon2, _Plugin);
  var _super = _createSuper(TopBackIcon2);
  function TopBackIcon2() {
    _classCallCheck(this, TopBackIcon2);
    return _super.apply(this, arguments);
  }
  _createClass(TopBackIcon2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this = this;
      this.initIcons();
      this.onClick = function(e) {
        e.preventDefault();
        e.stopPropagation();
        _this.config.onClick(e);
      };
      this.bind(["click", "touchend"], this.onClick);
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        screenBack: {
          icon: BackSVG,
          class: "xg-fullscreen-back"
        }
      };
    }
  }, {
    key: "initIcons",
    value: function initIcons() {
      var icons = this.icons;
      this.appendChild(this.root, icons.screenBack);
    }
  }, {
    key: "show",
    value: function show() {
      util.addClass(this.root, "show");
    }
  }, {
    key: "hide",
    value: function hide() {
      util.removeClass(this.root, "show");
    }
  }, {
    key: "render",
    value: function render() {
      return '<xg-icon class="xgplayer-back">\n    </xg-icon>';
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "topbackicon";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.ROOT_TOP,
        index: 0
      };
    }
  }]);
  return TopBackIcon2;
}(Plugin);
export { TopBackIcon as default };
