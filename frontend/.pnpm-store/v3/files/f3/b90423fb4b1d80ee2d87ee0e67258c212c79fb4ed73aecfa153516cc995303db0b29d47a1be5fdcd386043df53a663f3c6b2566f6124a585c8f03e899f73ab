import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { ENDED, TIME_UPDATE, URL_CHANGE, PLAY } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var Poster = /* @__PURE__ */ function(_Plugin) {
  _inherits(Poster2, _Plugin);
  var _super = _createSuper(Poster2);
  function Poster2() {
    _classCallCheck(this, Poster2);
    return _super.apply(this, arguments);
  }
  _createClass(Poster2, [{
    key: "isEndedShow",
    get: function get() {
      return this.config.isEndedShow;
    },
    set: function set(value) {
      this.config.isEndedShow = value;
    }
  }, {
    key: "hide",
    value: function hide() {
      util.addClass(this.root, "hide");
    }
  }, {
    key: "show",
    value: function show(value) {
      util.removeClass(this.root, "hide");
    }
  }, {
    key: "beforeCreate",
    value: function beforeCreate(args) {
      if (typeof args.player.config.poster === "string") {
        args.config.poster = args.player.config.poster;
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this = this;
      this.on(ENDED, function() {
        if (_this.isEndedShow) {
          util.removeClass(_this.root, "hide");
        }
      });
      if (this.config.hideCanplay) {
        this.once(TIME_UPDATE, function() {
          _this.onTimeUpdate();
        });
        this.on(URL_CHANGE, function() {
          util.removeClass(_this.root, "hide");
          util.addClass(_this.root, "xg-showplay");
          _this.once(TIME_UPDATE, function() {
            _this.onTimeUpdate();
          });
        });
      } else {
        this.on(PLAY, function() {
          util.addClass(_this.root, "hide");
        });
      }
    }
  }, {
    key: "setConfig",
    value: function setConfig(config) {
      var _this2 = this;
      Object.keys(config).forEach(function(key) {
        _this2.config[key] = config[key];
      });
      var poster = this.config.poster;
      this.update(poster);
    }
  }, {
    key: "onTimeUpdate",
    value: function onTimeUpdate() {
      var _this3 = this;
      if (!this.player.currentTime) {
        this.once(TIME_UPDATE, function() {
          _this3.onTimeUpdate();
        });
      } else {
        util.removeClass(this.root, "xg-showplay");
      }
    }
  }, {
    key: "update",
    value: function update(poster) {
      if (!poster) {
        return;
      }
      this.config.poster = poster;
      this.root.style.backgroundImage = "url(".concat(poster, ")");
    }
  }, {
    key: "getBgSize",
    value: function getBgSize(mode) {
      var _bg = "";
      switch (mode) {
        case "cover":
          _bg = "cover";
          break;
        case "contain":
          _bg = "contain";
          break;
        case "fixHeight":
          _bg = "auto 100%";
          break;
        default:
          _bg = "";
      }
      return _bg ? "background-size: ".concat(_bg, ";") : "";
    }
  }, {
    key: "render",
    value: function render() {
      var _this$config = this.config, poster = _this$config.poster, hideCanplay = _this$config.hideCanplay, fillMode = _this$config.fillMode, notHidden = _this$config.notHidden;
      var _bg = this.getBgSize(fillMode);
      var style = poster ? "background-image:url(".concat(poster, ");").concat(_bg) : _bg;
      var className = notHidden ? "xg-not-hidden" : hideCanplay ? "xg-showplay" : "";
      return '<xg-poster class="xgplayer-poster '.concat(className, '" style="').concat(style, '">\n    </xg-poster>');
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "poster";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        isEndedShow: true,
        hideCanplay: false,
        notHidden: false,
        poster: "",
        fillMode: "fixWidth"
      };
    }
  }]);
  return Poster2;
}(Plugin);
export { Poster as default };
