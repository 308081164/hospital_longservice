import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import { ERROR } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var ErrorPlugin = /* @__PURE__ */ function(_Plugin) {
  _inherits(ErrorPlugin2, _Plugin);
  var _super = _createSuper(ErrorPlugin2);
  function ErrorPlugin2() {
    _classCallCheck(this, ErrorPlugin2);
    return _super.apply(this, arguments);
  }
  _createClass(ErrorPlugin2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this = this;
      this.clickHandler = this.hook("errorRetry", this.errorRetry, {
        pre: function pre(e) {
          e.preventDefault();
          e.stopPropagation();
        }
      });
      this.onError = this.hook("showError", this.handleError);
      this.bind(".xgplayer-error-refresh", "click", this.clickHandler);
      this.on(ERROR, function(error) {
        _this.onError(error);
      });
    }
  }, {
    key: "errorRetry",
    value: function errorRetry(e) {
      this.emitUserAction(e, "error_retry", {});
      this.player.retry();
    }
  }, {
    key: "handleError",
    value: function handleError() {
      var error = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {};
      var player = this.player;
      var type = error.errorType;
      var errorNote = player.errorNote ? this.i18n[player.errorNote] : "";
      if (!errorNote) {
        switch (type) {
          case "decoder":
            errorNote = this.i18n.MEDIA_ERR_DECODE;
            break;
          case "network":
            errorNote = this.i18n.MEDIA_ERR_NETWORK;
            break;
          default:
            errorNote = this.i18n.MEDIA_ERR_SRC_NOT_SUPPORTED;
        }
      }
      this.find(".xgplayer-error-text").innerHTML = errorNote;
      this.find(".xgplayer-error-tips").innerHTML = "".concat(this.i18n.REFRESH_TIPS, '<span class="xgplayer-error-refresh">').concat(this.i18n.REFRESH, "</span>");
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.unbind(".xgplayer-error-refresh", "click", this.clickHandler);
    }
  }, {
    key: "render",
    value: function render() {
      return '<xg-error class="xgplayer-error">\n      <div class="xgplayer-errornote">\n       <span class="xgplayer-error-text"></span>\n       <span class="xgplayer-error-tips"><em class="xgplayer-error-refresh"></em></span>\n      </div>\n    </xg-error>';
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "error";
    }
  }]);
  return ErrorPlugin2;
}(Plugin);
export { ErrorPlugin as default };
