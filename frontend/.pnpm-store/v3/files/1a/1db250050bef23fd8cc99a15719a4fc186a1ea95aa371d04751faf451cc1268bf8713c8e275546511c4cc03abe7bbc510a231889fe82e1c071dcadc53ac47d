import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
import STATE_CLASS from "../../stateClassMap.js";
var Prompt = /* @__PURE__ */ function(_Plugin) {
  _inherits(Prompt2, _Plugin);
  var _super = _createSuper(Prompt2);
  function Prompt2() {
    _classCallCheck(this, Prompt2);
    return _super.apply(this, arguments);
  }
  _createClass(Prompt2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this = this;
      this.intervalId = 0;
      this.customConfig = null;
      this.bind(".highlight", ["click", "touchend"], function(e) {
        if (_this.config.onClick || _this.customOnClick) {
          e.preventDefault();
          e.stopPropagation();
          _this.customOnClick ? _this.customOnClick(e) : _this.config.onClick(e);
        }
      });
      this.player.showPrompt = function() {
        _this.showPrompt.apply(_this, arguments);
      };
      this.player.hidePrompt = function() {
        _this.hide();
      };
    }
  }, {
    key: "setStyle",
    value: function setStyle(style) {
      var _this2 = this;
      Object.keys(style).map(function(key) {
        _this2.root.style[key] = style[key];
      });
    }
  }, {
    key: "showPrompt",
    value: function showPrompt(detail) {
      var _this3 = this;
      var config = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
      var onClick = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : function() {
      };
      if (!detail) {
        return;
      }
      this.customOnClick = onClick;
      var interval = this.config.interval;
      if (this.intervalId) {
        clearTimeout(this.intervalId);
        this.intervalId = null;
      }
      util.addClass(this.root, "show");
      config.mode === "arrow" && util.addClass(this.root, "arrow");
      if (typeof detail === "string") {
        this.find(".xgplayer-prompt-detail").innerHTML = detail;
      } else {
        this.find(".xgplayer-prompt-detail").innerHTML = "".concat(detail.text || "") + "".concat(detail.highlight ? '<i class="highlight">'.concat(detail.highlight, "</i>") : "");
      }
      config.style && this.setStyle(config.style);
      var autoHide = typeof config.autoHide === "boolean" ? config.autoHide : this.config.autoHide;
      if (autoHide) {
        var hideinterval = config.interval || interval;
        this.intervalId = setTimeout(function() {
          _this3.hide();
        }, hideinterval);
      }
    }
  }, {
    key: "hide",
    value: function hide() {
      util.removeClass(this.root, "show");
      util.removeClass(this.root, "arrow");
      this.root.removeAttribute("style");
      this.customOnClick = null;
    }
  }, {
    key: "render",
    value: function render() {
      return '<xg-prompt class="xgplayer-prompt '.concat(STATE_CLASS.CONTROLS_FOLLOW, '">\n    <span class="xgplayer-prompt-detail"></span>\n    </xg-prompt>');
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "prompt";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        interval: 3e3,
        style: {},
        mode: "arrow",
        autoHide: true,
        detail: {
          text: "",
          highlight: ""
        },
        onClick: function onClick() {
        }
      };
    }
  }]);
  return Prompt2;
}(Plugin);
export { Prompt as default };
