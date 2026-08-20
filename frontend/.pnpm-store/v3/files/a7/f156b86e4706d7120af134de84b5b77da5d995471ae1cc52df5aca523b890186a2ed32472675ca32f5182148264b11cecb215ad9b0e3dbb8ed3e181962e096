import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized, createClass as _createClass } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import sniffer from "../../utils/sniffer.js";
import { TIME_UPDATE, COMPLETE, EMPTIED, PLAY, PAUSE, LOADED_DATA, LOAD_START } from "../../events.js";
import XG_DEBUG from "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var MODES = {
  REAL_TIME: "realtime",
  FIRST_FRAME: "firstframe",
  FRAME_RATE: "framerate",
  POSTER: "poster"
};
function nowTime() {
  try {
    return parseInt(window.performance.now(), 10);
  } catch (e) {
    return new Date().getTime();
  }
}
function checkIsSupportCanvas() {
  try {
    var ctx = document.createElement("canvas").getContext;
    if (ctx) {
      return true;
    }
    return false;
  } catch (e) {
    return false;
  }
}
var isSupportCanvas = null;
var DynamicBg = /* @__PURE__ */ function(_Plugin) {
  _inherits(DynamicBg2, _Plugin);
  var _super = _createSuper(DynamicBg2);
  function DynamicBg2() {
    var _this;
    _classCallCheck(this, DynamicBg2);
    for (var _len = arguments.length, args = new Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }
    _this = _super.call.apply(_super, [this].concat(args));
    _defineProperty(_assertThisInitialized(_this), "onLoadedData", function(e) {
      if (!_this.player) {
        return;
      }
      _this._frameCount = _this.config.startFrameCount;
      _this.stop();
      _this.renderOnTimeupdate(e);
      _this.off(TIME_UPDATE, _this.renderOnTimeupdate);
      _this.on(TIME_UPDATE, _this.renderOnTimeupdate);
    });
    _defineProperty(_assertThisInitialized(_this), "onVisibilitychange", function(e) {
      if (document.visibilityState === "visible") {
        _this._checkIfCanStart() && _this.start();
      } else if (document.visibilityState === "hidden") {
        _this.stop();
      }
    });
    _defineProperty(_assertThisInitialized(_this), "renderOnTimeupdate", function(e) {
      if (_this._frameCount > 0) {
        _this.renderOnce();
        _this._frameCount--;
      } else {
        _this._isLoaded = true;
        _this.off(TIME_UPDATE, _this.renderOnTimeupdate);
        var startInterval = _this.config.startInterval;
        !_this.player.paused && _this._checkIfCanStart() && _this.start(0, startInterval);
      }
    });
    _defineProperty(_assertThisInitialized(_this), "start", function(time, interval) {
      var video = _this.player.video;
      var _now = nowTime();
      var _sVideo = _this.checkVideoIsSupport(video);
      if (!_sVideo || !_this.canvasCtx) {
        return;
      }
      if (!interval) {
        interval = _this.interval;
      }
      _this.stop();
      if (video.videoWidth && video.videoHeight) {
        _this.videoPI = video.videoHeight > 0 ? parseInt(video.videoWidth / video.videoHeight * 100, 10) : 0;
        if (_this.config.mode === MODES.REAL_TIME) {
          video && video.videoWidth && _this.update(_sVideo, _this.videoPI);
          _this.preTime = _now;
        } else if (_now - _this.preTime >= interval) {
          video && video.videoWidth && _this.update(_sVideo, _this.videoPI);
          _this.preTime = _now;
        }
      }
      _this.frameId = _this._loopType === "timer" ? util.setTimeout(_assertThisInitialized(_this), _this.start, interval) : util.requestAnimationFrame(_this.start);
    });
    _defineProperty(_assertThisInitialized(_this), "stop", function() {
      if (_this.frameId) {
        _this._loopType === "timer" ? util.clearTimeout(_assertThisInitialized(_this), _this.frameId) : util.cancelAnimationFrame(_this.frameId);
        _this.frameId = null;
      }
    });
    return _this;
  }
  _createClass(DynamicBg2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      if (this.playerConfig.dynamicBg === true) {
        this.config.disable = false;
      }
      if (!DynamicBg2.isSupport) {
        this.config.disable = true;
      }
      var _this$config = this.config, disable = _this$config.disable, mode = _this$config.mode, frameRate = _this$config.frameRate;
      if (disable) {
        return;
      }
      this._pos = {
        width: 0,
        height: 0,
        rwidth: 0,
        rheight: 0,
        x: 0,
        y: 0,
        pi: 0
      };
      this.isStart = false;
      this._isLoaded = false;
      this.videoPI = 0;
      this.preTime = 0;
      this.interval = parseInt(1e3 / frameRate, 10);
      this.canvas = null;
      this.canvasCtx = null;
      this._frameCount = 0;
      this._loopType = this.config.mode !== MODES.REAL_TIME && this.interval >= 1e3 ? "timer" : "animation";
      this.once(COMPLETE, function() {
        if (!_this2.player) {
          return;
        }
        _this2.init();
        _this2.renderByPoster();
        if (!_this2.player.paused) {
          _this2.start();
        }
      });
      if (mode === MODES.POSTER) {
        return;
      }
      if (mode !== MODES.FIRST_FRAME) {
        this.on(EMPTIED, function() {
          _this2.stop();
        });
        this.on(PLAY, function() {
          var startInterval = _this2.config.startInterval;
          _this2._checkIfCanStart() && _this2.start(0, startInterval);
        });
        this.on(PAUSE, function() {
          _this2.stop();
        });
      }
      this.on(LOADED_DATA, this.onLoadedData);
      this.on(LOAD_START, function() {
        _this2._isLoaded = false;
        _this2.stop();
      });
      document.addEventListener("visibilitychange", this.onVisibilitychange);
    }
  }, {
    key: "setConfig",
    value: function setConfig(config) {
      var _this3 = this;
      Object.keys(config).forEach(function(key) {
        if (key === "root" && config[key] !== _this3.config[key]) {
          _this3.reRender(config[key]);
        } else if (key === "frameRate") {
          _this3.interval = parseInt(1e3 / config[key], 10);
        } else if (key === "disable" && config[key]) {
          _this3.stop();
        }
        _this3.config[key] = config[key];
      });
    }
  }, {
    key: "init",
    value: function init(_root) {
      var player = this.player, config = this.config;
      this.canvasFilter = DynamicBg2.supportCanvasFilter();
      try {
        var parent = _root || config.root;
        if (!parent) {
          parent = !config.isInnerRender ? player.root : player.innerContainer || player.root;
        }
        parent.insertAdjacentHTML("afterbegin", '<div class="xgplayer-dynamic-bg" data-index="'.concat(config.index, '"><canvas>\n        </canvas><xgmask></xgmask></div>'));
        this.root = parent.children[0];
        this.canvas = this.find("canvas");
        if (!this.canvasFilter) {
          this.canvas.style.filter = config.filter;
          this.canvas.style.webkitFilter = config.filter;
        }
        this.mask = this.find("xgmask");
        config.addMask && (this.mask.style.background = config.maskBg);
        this.canvasCtx = this.canvas.getContext("2d");
      } catch (e) {
        XG_DEBUG.logError("plugin:DynamicBg", e);
      }
    }
  }, {
    key: "reRender",
    value: function reRender(root) {
      var disable = this.config.disable;
      if (!disable && !this.root) {
        return;
      }
      this.stop();
      var _p = this.root ? this.root.parentElement : null;
      if (_p !== root) {
        _p.removeChild(this.root);
      }
      if (!root) {
        this.root = null;
        return;
      }
      this.init(root);
      this.renderOnce();
      var startInterval = this.config.startInterval;
      this._checkIfCanStart() && this.start(0, startInterval);
    }
  }, {
    key: "checkVideoIsSupport",
    value: function checkVideoIsSupport(video) {
      if (!video) {
        return null;
      }
      var _tVideo = video && video instanceof window.HTMLVideoElement ? video : video.canvas ? video.canvas : video.flyVideo ? video.flyVideo : null;
      if (_tVideo && !(sniffer.browser === "safari" && util.isMSE(_tVideo))) {
        return _tVideo;
      }
      var _tagName = _tVideo ? _tVideo.tagName.toLowerCase() : "";
      if (_tagName === "canvas" || _tagName === "img") {
        return _tVideo;
      }
      return null;
    }
  }, {
    key: "renderByPoster",
    value: function renderByPoster() {
      var poster = this.playerConfig.poster;
      if (poster) {
        var url = util.typeOf(poster) === "String" ? poster : util.typeOf(poster.poster) === "String" ? poster.poster : null;
        this.updateImg(url);
      }
    }
  }, {
    key: "_checkIfCanStart",
    value: function _checkIfCanStart() {
      var mode = this.config.mode;
      return this._isLoaded && !this.player.paused && mode !== MODES.FIRST_FRAME && mode !== MODES.POSTER;
    }
  }, {
    key: "renderOnce",
    value: function renderOnce() {
      var video = this.player.video;
      if (!video.videoWidth || !video.videoHeight) {
        return;
      }
      this.videoPI = parseInt(video.videoWidth / video.videoHeight * 100, 10);
      var _sVideo = this.checkVideoIsSupport(video);
      _sVideo && this.update(_sVideo, this.videoPI);
    }
  }, {
    key: "updateImg",
    value: function updateImg(url) {
      var _this4 = this;
      if (!url) {
        return;
      }
      var _this$canvas$getBound = this.canvas.getBoundingClientRect(), width = _this$canvas$getBound.width, height = _this$canvas$getBound.height;
      var image = new window.Image();
      image.onload = function() {
        if (!_this4.canvas || _this4.frameId || _this4.isStart) {
          return;
        }
        _this4.canvas.height = height;
        _this4.canvas.width = width;
        var pi = parseInt(width / height * 100, 10);
        _this4.update(image, pi);
        image = null;
      };
      image.src = url;
    }
  }, {
    key: "update",
    value: function update(video, sourcePI) {
      if (!this.canvas || !this.canvasCtx || !sourcePI) {
        return;
      }
      try {
        var _pos = this._pos, config = this.config;
        var _this$canvas$getBound2 = this.canvas.getBoundingClientRect(), width = _this$canvas$getBound2.width, height = _this$canvas$getBound2.height;
        if (width !== _pos.width || height !== _pos.height || _pos.pi !== sourcePI) {
          var pi = parseInt(width / height * 100, 10);
          _pos.pi = sourcePI;
          _pos.width !== width && (_pos.width = this.canvas.width = width);
          _pos.height !== height && (_pos.height = this.canvas.height = height);
          var rheight = height;
          var rwidth = width;
          if (pi < sourcePI) {
            rwidth = parseInt(height * sourcePI / 100, 10);
          } else if (pi > sourcePI) {
            rheight = parseInt(width * 100 / sourcePI, 10);
          }
          _pos.rwidth = rwidth * config.multiple;
          _pos.rheight = rheight * config.multiple;
          _pos.x = (width - _pos.rwidth) / 2;
          _pos.y = (height - _pos.rheight) / 2;
        }
        this.canvasFilter && (this.canvasCtx.filter = config.filter);
        this.canvasCtx.drawImage(video, _pos.x, _pos.y, _pos.rwidth, _pos.rheight);
      } catch (e) {
        XG_DEBUG.logError("plugin:DynamicBg", e);
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.stop();
      document.removeEventListener("visibilitychange", this.onVisibilitychange);
      this.canvasCtx = null;
      this.canvas = null;
    }
  }, {
    key: "render",
    value: function render() {
      return "";
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "dynamicBg";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        isInnerRender: false,
        disable: true,
        index: -1,
        mode: "framerate",
        frameRate: 10,
        filter: "blur(50px)",
        startFrameCount: 2,
        startInterval: 0,
        addMask: true,
        multiple: 1.2,
        maskBg: "rgba(0,0,0,0.7)"
      };
    }
  }, {
    key: "isSupport",
    get: function get() {
      if (typeof isSupportCanvas === "boolean") {
        return isSupportCanvas;
      }
      isSupportCanvas = checkIsSupportCanvas();
      return isSupportCanvas;
    }
  }, {
    key: "supportCanvasFilter",
    value: function supportCanvasFilter() {
      return !(sniffer.browser === "safari" || sniffer.browser === "firefox");
    }
  }]);
  return DynamicBg2;
}(Plugin);
export { DynamicBg as default };
