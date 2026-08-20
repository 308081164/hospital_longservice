import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck } from "../../_virtual/_rollupPluginBabelHelpers.js";
import Plugin, { POSITIONS } from "../../plugin/plugin.js";
import loadingIcon from "../assets/loading.js";
var Loading = /* @__PURE__ */ function(_Plugin) {
  _inherits(Loading2, _Plugin);
  var _super = _createSuper(Loading2);
  function Loading2() {
    _classCallCheck(this, Loading2);
    return _super.apply(this, arguments);
  }
  _createClass(Loading2, [{
    key: "registerIcons",
    value: function registerIcons() {
      return {
        loadingIcon
      };
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      this.appendChild("xg-loading-inner", this.icons.loadingIcon);
    }
  }, {
    key: "render",
    value: function render() {
      return '\n    <xg-loading class="xgplayer-loading">\n      <xg-loading-inner></xg-loading-inner>\n    </xg-loading>';
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "loading";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.ROOT
      };
    }
  }]);
  return Loading2;
}(Plugin);
export { Loading as default };
