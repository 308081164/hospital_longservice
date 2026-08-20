import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import { MINI_STATE_CHANGE } from "../../events.js";
import "../../utils/debug.js";
import Plugin, { POSITIONS } from "../../plugin/plugin.js";
import STATE_CLASS from "../../stateClassMap.js";
var Controls = /* @__PURE__ */ function(_Plugin) {
  _inherits(Controls2, _Plugin);
  var _super = _createSuper(Controls2);
  function Controls2() {
    var _this;
    _classCallCheck(this, Controls2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "onMouseEnter", function(e) {
      var _assertThisInitialize = _assertThisInitialized(_this), player = _assertThisInitialize.player, playerConfig = _assertThisInitialize.playerConfig;
      playerConfig.closeControlsBlur && player.focus({
        autoHide: false
      });
    });
    _defineProperty(_assertThisInitialized(_this), "onMouseLeave", function(e) {
      var _assertThisInitialize2 = _assertThisInitialized(_this), player = _assertThisInitialize2.player;
      player.focus();
    });
    return _this;
  }
  _createClass(Controls2, [{
    key: "beforeCreate",
    value: function beforeCreate(args) {
      if (!args.config.mode && sniffer.device === "mobile") {
        args.config.mode = "flex";
      }
      if (args.player.config.marginControls) {
        args.config.autoHide = false;
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      var _this$config = this.config, disable = _this$config.disable, height = _this$config.height, mode = _this$config.mode;
      if (disable) {
        return;
      }
      mode === "flex" && this.player.addClass(STATE_CLASS.FLEX_CONTROLS);
      var style = {
        height: "".concat(height, "px")
      };
      Object.keys(style).map(function(key) {
        _this2.root.style[key] = style[key];
      });
      this.left = this.find("xg-left-grid");
      this.center = this.find("xg-center-grid");
      this.right = this.find("xg-right-grid");
      this.innerRoot = this.find("xg-inner-controls");
      this.on(MINI_STATE_CHANGE, function(isMini) {
        isMini ? util.addClass(_this2.root, "mini-controls") : util.removeClass(_this2.root, "mini-controls");
      });
      var isMobileSimulateMode = this.playerConfig.isMobileSimulateMode;
      if (sniffer.device !== "mobile" && isMobileSimulateMode !== "mobile") {
        this.bind("mouseenter", this.onMouseEnter);
        this.bind("mouseleave", this.onMouseLeave);
      }
    }
  }, {
    key: "focus",
    value: function focus() {
      this.player.focus({
        autoHide: false
      });
    }
  }, {
    key: "focusAwhile",
    value: function focusAwhile() {
      this.player.focus({
        autoHide: true
      });
    }
  }, {
    key: "blur",
    value: function blur() {
      this.player.blur({
        ignorePaused: true
      });
    }
  }, {
    key: "recoverAutoHide",
    value: function recoverAutoHide() {
      this.config.autoHide && util.addClass(this.root, STATE_CLASS.CONTROLS_AUTOHIDE);
    }
  }, {
    key: "pauseAutoHide",
    value: function pauseAutoHide() {
      util.removeClass(this.root, STATE_CLASS.CONTROLS_AUTOHIDE);
    }
  }, {
    key: "show",
    value: function show(value) {
      this.root.style.display = "";
      this.player.focus();
    }
  }, {
    key: "hide",
    value: function hide() {
      this.root.style.display = "none";
    }
  }, {
    key: "mode",
    get: function get() {
      return this.config.mode;
    }
  }, {
    key: "registerPlugin",
    value: function registerPlugin(plugin) {
      var options = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
      var name = arguments.length > 2 ? arguments[2] : void 0;
      if (!this.root) {
        return;
      }
      var defaultConfig = plugin.defaultConfig || {};
      if (!options.root) {
        var position = options.position ? options.position : options.config && options.config.position ? options.config.position : defaultConfig.position;
        switch (position) {
          case POSITIONS.CONTROLS_LEFT:
            options.root = this.left;
            break;
          case POSITIONS.CONTROLS_RIGHT:
            options.root = this.right;
            break;
          case POSITIONS.CONTROLS_CENTER:
            options.root = this.center;
            break;
          case POSITIONS.CONTROLS:
            options.root = this.root;
            break;
          default:
            options.root = this.left;
        }
        return _get(_getPrototypeOf(Controls2.prototype), "registerPlugin", this).call(this, plugin, options, name);
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      if (sniffer.device !== "mobile") {
        this.unbind("mouseenter", this.onMouseEnter);
        this.unbind("mouseleave", this.onMouseLeave);
      }
      this.left = null;
      this.center = null;
      this.right = null;
      this.innerRoot = null;
    }
  }, {
    key: "render",
    value: function render() {
      var _this$config2 = this.config, mode = _this$config2.mode, autoHide = _this$config2.autoHide, initShow = _this$config2.initShow, disable = _this$config2.disable;
      if (disable) {
        return;
      }
      var className = util.classNames({
        "xgplayer-controls": true
      }, {
        "flex-controls": mode === "flex"
      }, {
        "bottom-controls": mode === "bottom"
      }, _defineProperty({}, STATE_CLASS.CONTROLS_AUTOHIDE, autoHide), {
        "xgplayer-controls-initshow": initShow || !autoHide
      });
      return '<xg-controls class="'.concat(className, '" unselectable="on">\n    <xg-inner-controls class="xg-inner-controls xg-pos">\n      <xg-left-grid class="xg-left-grid">\n      </xg-left-grid>\n      <xg-center-grid class="xg-center-grid"></xg-center-grid>\n      <xg-right-grid class="xg-right-grid">\n      </xg-right-grid>\n    </xg-inner-controls>\n    </xg-controls>');
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "controls";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        disable: false,
        autoHide: true,
        mode: "",
        initShow: false
      };
    }
  }]);
  return Controls2;
}(Plugin);
export { Controls as default };
