import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import { VIDEO_RESIZE, SCREEN_SHOT } from "../../events.js";
import "../../utils/debug.js";
import { POSITIONS } from "../../plugin/plugin.js";
import Icon from "../common/iconPlugin.js";
var ScreenShot = /* @__PURE__ */ function(_IconPlugin) {
  _inherits(ScreenShot2, _IconPlugin);
  var _super = _createSuper(ScreenShot2);
  function ScreenShot2() {
    _classCallCheck(this, ScreenShot2);
    return _super.apply(this, arguments);
  }
  _createClass(ScreenShot2, [{
    key: "beforeCreate",
    value: function beforeCreate(args) {
      if (typeof args.player.config.screenShot === "boolean") {
        args.config.disable = !args.player.config.screenShot;
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      _get(_getPrototypeOf(ScreenShot2.prototype), "afterCreate", this).call(this);
      this.appendChild(".xgplayer-icon", this.icons.screenshotIcon);
      var config = this.config;
      this.initSize = function(data) {
        if (config.fitVideo) {
          config.width = data.vWidth;
          config.height = data.vHeight;
        }
      };
      this.once(VIDEO_RESIZE, this.initSize);
    }
  }, {
    key: "onPluginsReady",
    value: function onPluginsReady() {
      this.show();
      this.onClickBtn = this.onClickBtn.bind(this);
      this.bind(["click", "touchend"], this.onClickBtn);
    }
  }, {
    key: "saveScreenShot",
    value: function saveScreenShot(data, filename) {
      var saveLink = document.createElement("a");
      saveLink.href = data;
      saveLink.download = filename;
      var event;
      try {
        if (typeof MouseEvent !== "undefined") {
          event = new MouseEvent("click", {
            bubbles: true,
            cancelable: true,
            view: window
          });
        } else {
          event = document.createEvent("MouseEvents");
          event.initMouseEvent("click", true, false, window, 0, 0, 0, 0, 0, false, false, false, false, 0, null);
        }
      } catch (e) {
        console.error("MouseEvent unsupported", e);
      }
      if (event) {
        saveLink.dispatchEvent(event);
      }
    }
  }, {
    key: "createCanvas",
    value: function createCanvas(width, height) {
      var canvas = document.createElement("canvas");
      var canvasCtx = canvas.getContext("2d");
      this.canvasCtx = canvasCtx;
      this.canvas = canvas;
      canvas.width = width || this.config.width;
      canvas.height = height || this.config.height;
      canvasCtx.imageSmoothingEnabled = true;
      if (canvasCtx.imageSmoothingEnabled) {
        canvasCtx.imageSmoothingQuality = "high";
      }
    }
  }, {
    key: "onClickBtn",
    value: function onClickBtn(e) {
      var _this = this;
      e.preventDefault();
      e.stopPropagation();
      this.emitUserAction(e, "shot");
      var config = this.config;
      this.shot(config.width, config.height).then(function(data) {
        _this.emit(SCREEN_SHOT, data);
        if (config.saveImg) {
          _this.saveScreenShot(data, config.name + config.format);
        }
      });
    }
  }, {
    key: "shot",
    value: function shot(width, height) {
      var _this2 = this;
      var option = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {
        quality: 0.92,
        type: "image/png"
      };
      var config = this.config, player = this.player;
      var quality = option.quality || config.quality;
      var type = option.type || config.type;
      return new Promise(function(resolve, reject) {
        var canvas = null;
        var canvasCtx;
        if (player.media.canvas) {
          canvas = player.media.canvas;
        } else {
          if (!_this2.canvas) {
            _this2.createCanvas(width, height);
          } else {
            _this2.canvas.width = width || config.width;
            _this2.canvas.height = height || config.height;
          }
          canvas = _this2.canvas;
          canvasCtx = _this2.canvasCtx;
          var mediaRatio = player.media.videoWidth / player.media.videoHeight;
          var canvasRatio = canvas.width / canvas.height;
          var sx = 0, sy = 0, sw = player.media.videoWidth, sh = player.media.videoHeight;
          var dx, dy, dw, dh;
          if (mediaRatio > canvasRatio) {
            dw = canvas.width;
            dh = canvas.width / mediaRatio;
            dx = 0;
            dy = Math.round((canvas.height - dh) / 2);
          } else if (mediaRatio === canvasRatio) {
            dw = canvas.width;
            dh = canvas.height;
            dx = 0;
            dy = 0;
          } else if (mediaRatio < canvasRatio) {
            dw = canvas.height * mediaRatio;
            dh = canvas.height;
            dx = Math.round((canvas.width - dw) / 2);
            dy = 0;
          }
          canvasCtx.drawImage(player.media, sx, sy, sw, sh, dx, dy, dw, dh);
        }
        var src = canvas.toDataURL(type, quality).replace(type, "image/octet-stream");
        src = src.replace(/^data:image\/[^;]+/, "data:application/octet-stream");
        resolve(src);
      });
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        screenshotIcon: null
      };
    }
  }, {
    key: "destroy",
    value: function destroy() {
      _get(_getPrototypeOf(ScreenShot2.prototype), "destroy", this).call(this);
      this.unbind(["click", "touchend"], this.onClickBtn);
      this.off(VIDEO_RESIZE, this.initSize);
    }
  }, {
    key: "render",
    value: function render() {
      if (this.config.disable) {
        return;
      }
      var className = this.icons.screenshotIcon ? "xgplayer-icon" : "xgplayer-icon btn-text";
      var langKey = "SCREENSHOT";
      return '\n      <xg-icon class="xgplayer-shot">\n      <div class="'.concat(className, '">\n      ').concat(this.icons.screenshotIcon ? "" : '<span lang-key="'.concat(this.i18nKeys[langKey], '">').concat(this.i18n[langKey], "</span>"), "\n      </div>\n    </xg-icon>");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "screenShot";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 5,
        quality: 0.92,
        type: "image/png",
        format: ".png",
        width: 600,
        height: 337,
        saveImg: true,
        fitVideo: true,
        disable: false,
        name: "screenshot"
      };
    }
  }]);
  return ScreenShot2;
}(Icon);
export { ScreenShot as default };
