import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var Enter = /* @__PURE__ */ function(_Plugin) {
  _inherits(Enter2, _Plugin);
  var _super = _createSuper(Enter2);
  function Enter2() {
    _classCallCheck(this, Enter2);
    return _super.apply(this, arguments);
  }
  _createClass(Enter2, [{
    key: "render",
    value: function render() {
      var innerHtml = this.config.innerHtml;
      var root = util.createDom("xg-enter", "", {}, "xgplayer-enter");
      if (innerHtml && innerHtml instanceof window.HTMLElement) {
        root.appendChild(innerHtml);
      } else if (innerHtml && typeof innerHtml === "string") {
        root.innerHTML = innerHtml;
      } else {
        var barStr = "";
        for (var i = 1; i <= 12; i++) {
          barStr += '<div class="xgplayer-enter-bar'.concat(i, '"></div>');
        }
        root.innerHTML = '<div class="xgplayer-enter-spinner">'.concat(barStr, "</div>");
      }
      return root;
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "enter";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        innerHtml: "",
        logo: ""
      };
    }
  }]);
  return Enter2;
}(Plugin);
export { Enter as default };
