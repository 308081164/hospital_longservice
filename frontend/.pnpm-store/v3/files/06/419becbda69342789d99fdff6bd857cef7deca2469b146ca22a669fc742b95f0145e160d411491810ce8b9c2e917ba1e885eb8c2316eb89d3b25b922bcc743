import { defineProperty as _defineProperty, inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, assertThisInitialized as _assertThisInitialized, possibleConstructorReturn as _possibleConstructorReturn, get as _get, getPrototypeOf as _getPrototypeOf, objectSpread2 as _objectSpread2 } from "./_virtual/_rollupPluginBabelHelpers.js";
import MediaProxy from "./mediaProxy.js";
import util, { checkIsCurrentVideo } from "./utils/util.js";
export { default as Util } from "./utils/util.js";
import sniffer from "./utils/sniffer.js";
export { default as Sniffer } from "./utils/sniffer.js";
import INDEXDB from "./utils/database.js";
import Errors from "./error.js";
export { default as Errors } from "./error.js";
import { CANPLAY, USER_ACTION, FULLSCREEN_CHANGE, READY, ERROR, LOADED_DATA, PLAY, URL_NULL, COMPLETE, AUTOPLAY_STARTED, AUTOPLAY_PREVENTED, SEEKED, DEFINITION_CHANGE, AFTER_DEFINITION_CHANGE, RESET, DESTROY, CSS_FULLSCREEN_CHANGE, PLAYER_FOCUS, PLAYER_BLUR, DURATION_CHANGE, LOADING, VIDEO_RESIZE, REPLAY, PLAYNEXT } from "./events.js";
import * as events from "./events.js";
export { events as Events };
import { PLATER_ID, FULLSCREEN_EVENTS, GET_FULLSCREEN_API, EXIT_FULLSCREEN_API } from "./constant.js";
import { POSITIONS } from "./plugin/plugin.js";
export { default as Plugin } from "./plugin/plugin.js";
import BasePlugin from "./plugin/basePlugin.js";
export { default as BasePlugin } from "./plugin/basePlugin.js";
import pluginsManager from "./plugin/pluginsManager.js";
import STATE_CLASS from "./stateClassMap.js";
export { default as STATE_CLASS } from "./stateClassMap.js";
import getDefaultConfig from "./defaultConfig.js";
import { usePreset } from "./plugin/preset.js";
import hooksDescriptor, { runHooks, delHooksDescriptor, hook, useHooks, removeHooks, usePluginHooks, removePluginHooks } from "./plugin/hooksDescriptor.js";
import Controls from "./plugins/controls/index.js";
import XG_DEBUG, { bindDebug } from "./utils/debug.js";
import I18N from "./lang/i18n.js";
export { default as I18N } from "./lang/i18n.js";
import version from "./version.js";
import { STATES, STATE_ARRAY } from "./state.js";
import { InstManager, checkPlayerRoot } from "./instManager.js";
var PlAYER_HOOKS = ["play", "pause", "replay", "retry"];
var REAL_TIME_SPEED = 0;
var AVG_SPEED = 0;
var instManager = null;
var Player = /* @__PURE__ */ function(_MediaProxy) {
  _inherits(Player2, _MediaProxy);
  var _super = _createSuper(Player2);
  function Player2(options) {
    var _this;
    _classCallCheck(this, Player2);
    var _config = util.deepMerge(getDefaultConfig(), options);
    _this = _super.call(this, _config);
    _defineProperty(_assertThisInitialized(_this), "canPlayFunc", function() {
      if (!_this.config) {
        return;
      }
      var _this$config = _this.config, autoplay = _this$config.autoplay, defaultPlaybackRate = _this$config.defaultPlaybackRate;
      XG_DEBUG.logInfo("player", "canPlayFunc, startTime", _this.__startTime);
      _this._seekToStartTime();
      _this.playbackRate = defaultPlaybackRate;
      (autoplay || _this._useAutoplay) && _this.mediaPlay();
      _this.off(CANPLAY, _this.canPlayFunc);
      _this.removeClass(STATE_CLASS.ENTER);
    });
    _defineProperty(_assertThisInitialized(_this), "onFullscreenChange", function(event, isFullScreen) {
      var delayResize = function delayResize2() {
        util.setTimeout(_assertThisInitialized(_this), function() {
          _this.resize();
        }, 100);
      };
      var fullEl = util.getFullScreenEl();
      if (_this._fullActionFrom) {
        _this._fullActionFrom = "";
      } else {
        _this.emit(USER_ACTION, {
          eventType: "system",
          action: "switch_fullscreen",
          pluginName: "player",
          currentTime: _this.currentTime,
          duration: _this.duration,
          props: [{
            prop: "fullscreen",
            from: true,
            to: false
          }]
        });
      }
      var isVideo = checkIsCurrentVideo(fullEl, _this.playerId, PLATER_ID);
      if (isFullScreen || fullEl && (fullEl === _this._fullscreenEl || isVideo)) {
        delayResize();
        !_this.config.closeFocusVideoFocus && _this.media.focus();
        _this.fullscreen = true;
        _this.changeFullStyle(_this.root, fullEl, STATE_CLASS.FULLSCREEN);
        _this.emit(FULLSCREEN_CHANGE, true, _this._fullScreenOffset);
        if (_this.cssfullscreen) {
          _this.exitCssFullscreen();
        }
      } else if (_this.fullscreen) {
        delayResize();
        var _assertThisInitialize = _assertThisInitialized(_this), _fullScreenOffset = _assertThisInitialize._fullScreenOffset, config = _assertThisInitialize.config;
        if (config.needFullscreenScroll) {
          window.scrollTo(_fullScreenOffset.left, _fullScreenOffset.top);
          util.setTimeout(_assertThisInitialized(_this), function() {
            _this.fullscreen = false;
            _this._fullScreenOffset = null;
          }, 100);
        } else {
          !_this.config.closeFocusVideoFocus && _this.media.focus();
          _this.fullscreen = false;
          _this._fullScreenOffset = null;
        }
        if (!_this.cssfullscreen) {
          var el = _this._fullscreenEl;
          if (!el && (_this.root.contains(event.target) || event.target === _this.root)) {
            el = event.target;
          }
          _this.recoverFullStyle(_this.root, el, STATE_CLASS.FULLSCREEN);
        } else {
          _this.removeClass(STATE_CLASS.FULLSCREEN);
        }
        _this._fullscreenEl = null;
        _this.emit(FULLSCREEN_CHANGE, false);
      }
    });
    _defineProperty(_assertThisInitialized(_this), "_onWebkitbeginfullscreen", function(e) {
      _this._fullscreenEl = _this.media;
      _this.onFullscreenChange(e, true);
    });
    _defineProperty(_assertThisInitialized(_this), "_onWebkitendfullscreen", function(e) {
      _this.onFullscreenChange(e, false);
    });
    hooksDescriptor(_assertThisInitialized(_this), PlAYER_HOOKS);
    _this.config = _config;
    _this._pluginInfoId = util.generateSessionId();
    bindDebug(_assertThisInitialized(_this));
    var defaultPreset = _this.constructor.defaultPreset;
    if (_this.config.presets.length) {
      var defaultIdx = _this.config.presets.indexOf("default");
      if (defaultIdx >= 0 && defaultPreset) {
        _this.config.presets[defaultIdx] = defaultPreset;
      }
    } else if (defaultPreset) {
      _this.config.presets.push(defaultPreset);
    }
    _this.userTimer = null;
    _this.waitTimer = null;
    _this.handleSource = true;
    _this._state = STATES.INITIAL;
    _this.isAd = false;
    _this.isError = false;
    _this._hasStart = false;
    _this.isSeeking = false;
    _this.isCanplay = false;
    _this._useAutoplay = false;
    _this.__startTime = -1;
    _this.rotateDeg = 0;
    _this.isActive = false;
    _this.fullscreen = false;
    _this.cssfullscreen = false;
    _this.isRotateFullscreen = false;
    _this._fullscreenEl = null;
    _this.timeSegments = [];
    _this._cssfullscreenEl = null;
    _this.curDefinition = null;
    _this._orgCss = "";
    _this._fullScreenOffset = null;
    _this._videoHeight = 0;
    _this._videoWidth = 0;
    _this.videoPos = {
      pi: 1,
      scale: 0,
      rotate: -1,
      x: 0,
      y: 0,
      h: -1,
      w: -1,
      vy: 0,
      vx: 0
    };
    _this.sizeInfo = {
      width: 0,
      height: 0,
      left: 0,
      top: 0
    };
    _this._accPlayed = {
      t: 0,
      acc: 0,
      loopAcc: 0
    };
    _this._offsetInfo = {
      currentTime: -1,
      duration: 0
    };
    _this.innerContainer = null;
    _this.controls = null;
    _this.topBar = null;
    _this.root = null;
    _this.__i18n = I18N.init(_this._pluginInfoId);
    if (sniffer.os.isAndroid && sniffer.osVersion > 0 && sniffer.osVersion < 6) {
      _this.config.autoplay = false;
    }
    _this.database = new INDEXDB();
    _this.isUserActive = false;
    _this._onceSeekCanplay = null;
    _this._isPauseBeforeSeek = 0;
    _this.innerStates = {
      isActiveLocked: false
    };
    _this.instManager = instManager;
    var rootInit = _this._initDOM();
    if (!rootInit) {
      console.error(new Error("can't find the dom which id is ".concat(_this.config.id, " or this.config.el does not exist")));
      return _possibleConstructorReturn(_this);
    }
    var _this$config2 = _this.config, _this$config2$definit = _this$config2.definition, definition = _this$config2$definit === void 0 ? {} : _this$config2$definit, url = _this$config2.url;
    if (!url && definition.list && definition.list.length > 0) {
      var defaultDefinitionObj = definition.list.find(function(e) {
        return e.definition && e.definition === definition.defaultDefinition;
      });
      if (!defaultDefinitionObj) {
        definition.defaultDefinition = definition.list[0].definition;
        defaultDefinitionObj = definition.list[0];
      }
      _this.config.url = defaultDefinitionObj.url;
      _this.curDefinition = defaultDefinitionObj;
    }
    _this._bindEvents();
    _this._registerPresets();
    _this._registerPlugins();
    pluginsManager.onPluginsReady(_assertThisInitialized(_this));
    _this.getInitDefinition();
    _this.setState(STATES.READY);
    util.setTimeout(_assertThisInitialized(_this), function() {
      _this.emit(READY);
    }, 0);
    _this.onReady && _this.onReady();
    if (_this.config.videoInit || _this.config.autoplay) {
      if (!_this.hasStart || _this.state < STATES.ATTACHED) {
        _this.start();
      }
    }
    return _this;
  }
  _createClass(Player2, [{
    key: "_initDOM",
    value: function _initDOM() {
      var _instManager, _this2 = this;
      this.root = this.config.id ? document.getElementById(this.config.id) : null;
      if (!this.root) {
        var el = this.config.el;
        if (el && el.nodeType === 1) {
          this.root = el;
        } else {
          this.emit(ERROR, new Errors("use", this.config.vid, {
            line: 32,
            handle: "Constructor",
            msg: "container id can't be empty"
          }));
          console.error("this.confg.id or this.config.el can't be empty");
          return false;
        }
      }
      var ret = checkPlayerRoot(this.root);
      if (ret) {
        XG_DEBUG.logWarn("The is an Player instance already exists in this.root, destroy it and reinitialize");
        ret.destroy();
      }
      this.root.setAttribute(PLATER_ID, this.playerId);
      this.media.setAttribute(PLATER_ID, this.playerId);
      (_instManager = instManager) === null || _instManager === void 0 ? void 0 : _instManager.add(this);
      pluginsManager.init(this);
      this._initBaseDoms();
      if (this.config.controls) {
        var _root = this.config.controls.root || null;
        var controls = pluginsManager.register(this, Controls, {
          root: _root
        });
        this.controls = controls;
      }
      var device = this.config.isMobileSimulateMode === "mobile" ? "mobile" : sniffer.device;
      this.addClass("".concat(STATE_CLASS.DEFAULT, " ").concat(STATE_CLASS.INACTIVE, " xgplayer-").concat(device, " ").concat(this.config.controls ? "" : STATE_CLASS.NO_CONTROLS));
      if (this.config.autoplay) {
        this.addClass(STATE_CLASS.ENTER);
      } else {
        this.addClass(STATE_CLASS.NO_START);
      }
      if (this.config.fluid) {
        var _this$config3 = this.config, _width = _this$config3.width, _height = _this$config3.height;
        if (typeof _width !== "number" || typeof _height !== "number") {
          _width = 600;
          _height = 337.5;
        }
        var style = {
          width: "100%",
          height: "0",
          "max-width": "100%",
          "padding-top": "".concat(_height * 100 / _width, "%")
        };
        Object.keys(style).forEach(function(key) {
          _this2.root.style[key] = style[key];
        });
      } else {
        ["width", "height"].forEach(function(key) {
          if (_this2.config[key]) {
            if (typeof _this2.config[key] !== "number") {
              _this2.root.style[key] = _this2.config[key];
            } else {
              _this2.root.style[key] = "".concat(_this2.config[key], "px");
            }
          }
        });
      }
      var _this$root$getBoundin = this.root.getBoundingClientRect(), width = _this$root$getBoundin.width, height = _this$root$getBoundin.height, left = _this$root$getBoundin.left, top = _this$root$getBoundin.top;
      this.sizeInfo.width = width;
      this.sizeInfo.height = height;
      this.sizeInfo.left = left;
      this.sizeInfo.top = top;
      return true;
    }
  }, {
    key: "_initBaseDoms",
    value: function _initBaseDoms() {
      this.topBar = null;
      this.leftBar = null;
      this.rightBar = null;
      if (this.config.marginControls) {
        this.innerContainer = util.createDom("xg-video-container", "", {
          "data-index": -1
        }, "xg-video-container");
        this.root.appendChild(this.innerContainer);
      }
    }
  }, {
    key: "_bindEvents",
    value: function _bindEvents() {
      var _this3 = this;
      ["focus", "blur"].forEach(function(item) {
        _this3.on(item, _this3["on" + item.charAt(0).toUpperCase() + item.slice(1)]);
      });
      FULLSCREEN_EVENTS.forEach(function(item) {
        document && document.addEventListener(item, _this3.onFullscreenChange);
      });
      if (sniffer.os.isIos) {
        this.media.addEventListener("webkitbeginfullscreen", this._onWebkitbeginfullscreen);
        this.media.addEventListener("webkitendfullscreen", this._onWebkitendfullscreen);
      }
      this.once(LOADED_DATA, this.resize);
      this.playFunc = function() {
        if (!_this3.config.closeFocusVideoFocus) {
          _this3.media.focus();
        }
      };
      this.once(PLAY, this.playFunc);
    }
  }, {
    key: "_unbindEvents",
    value: function _unbindEvents() {
      var _this4 = this;
      this.root.removeEventListener("mousemove", this.mousemoveFunc);
      FULLSCREEN_EVENTS.forEach(function(item) {
        document.removeEventListener(item, _this4.onFullscreenChange);
      });
      this.playFunc && this.off(PLAY, this.playFunc);
      this.off(CANPLAY, this.canPlayFunc);
      this.media.removeEventListener("webkitbeginfullscreen", this._onWebkitbeginfullscreen);
      this.media.removeEventListener("webkitendfullscreen", this._onWebkitendfullscreen);
    }
  }, {
    key: "_clearUserTimer",
    value: function _clearUserTimer() {
      if (!this.userTimer) {
        return;
      }
      util.clearTimeout(this, this.userTimer);
      this.userTimer = null;
    }
  }, {
    key: "_startInit",
    value: function _startInit(url) {
      var _this5 = this;
      var options = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {};
      if (!this.media) {
        return;
      }
      if (!url || url === "" || util.typeOf(url) === "Array" && url.length === 0) {
        url = "";
        this.emit(URL_NULL);
        XG_DEBUG.logWarn("config.url is null, please get url and run player._startInit(url)");
        if (this.config.nullUrlStart) {
          return;
        }
      }
      if (util.isNotNull(options.playSessionId)) {
        this._playSessionId = options.playSessionId;
      }
      if (this.handleSource) {
        this._detachSourceEvents(this.media);
        if (util.typeOf(url) === "Array" && url.length > 0) {
          this._attachSourceEvents(this.media, url);
        } else if (!this.media.src || this.media.src !== url) {
          this.media.src = url;
        } else if (!url) {
          this.media.removeAttribute("src");
        }
      }
      if (util.typeOf(this.config.volume) === "Number") {
        this.volume = this.config.volume;
      }
      var _root = this.innerContainer ? this.innerContainer : this.root;
      if (this.media instanceof window.Element && !_root.contains(this.media)) {
        _root.insertBefore(this.media, _root.firstChild);
      }
      var readyState = this.media.readyState;
      XG_DEBUG.logInfo("_startInit readyState", readyState);
      if (this.config.autoplay) {
        !util.isMSE(this.media) && this.load();
        (sniffer.os.isIpad || sniffer.os.isPhone) && this.mediaPlay();
      }
      var startTime = this.config.startTime;
      this.__startTime = startTime > 0 ? startTime : -1;
      this.config.startTime = 0;
      if (readyState >= 2 && this.duration > 0) {
        this.canPlayFunc();
      } else {
        this.on(CANPLAY, this.canPlayFunc);
      }
      if (!this.hasStart || this.state < STATES.ATTACHED) {
        pluginsManager.afterInit(this);
      }
      this.hasStart = true;
      this.setState(STATES.ATTACHED);
      util.setTimeout(this, function() {
        _this5.emit(COMPLETE);
      }, 0);
    }
  }, {
    key: "_registerPlugins",
    value: function _registerPlugins() {
      var _this6 = this;
      var isInit = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : true;
      this._loadingPlugins = [];
      var ignores = this.config.ignores || [];
      var plugins = this.config.plugins || [];
      var i18n = this.config.i18n || [];
      isInit && I18N.extend(i18n, this.__i18n);
      var ignoresStr = ignores.join("||").toLowerCase().split("||");
      var cuPlugins = this.plugins;
      plugins.forEach(function(plugin) {
        try {
          var pluginName = plugin.plugin ? plugin.plugin.pluginName : plugin.pluginName;
          if (pluginName && ignoresStr.indexOf(pluginName.toLowerCase()) > -1) {
            return null;
          }
          if (!isInit && cuPlugins[pluginName.toLowerCase()]) {
            return;
          }
          if (plugin.lazy && plugin.loader) {
            var loadingPlugin = pluginsManager.lazyRegister(_this6, plugin);
            if (plugin.forceBeforeInit) {
              loadingPlugin.then(function() {
                _this6._loadingPlugins.splice(_this6._loadingPlugins.indexOf(loadingPlugin), 1);
              }).catch(function(e) {
                XG_DEBUG.logError("_registerPlugins:loadingPlugin", e);
                _this6._loadingPlugins.splice(_this6._loadingPlugins.indexOf(loadingPlugin), 1);
              });
              _this6._loadingPlugins.push(loadingPlugin);
            }
            return;
          }
          return _this6.registerPlugin(plugin);
        } catch (err) {
          XG_DEBUG.logError("_registerPlugins:", err);
        }
      });
    }
  }, {
    key: "_registerPresets",
    value: function _registerPresets() {
      var _this7 = this;
      this.config.presets.forEach(function(preset) {
        usePreset(_this7, preset);
      });
    }
  }, {
    key: "_getRootByPosition",
    value: function _getRootByPosition(position) {
      var _root = null;
      switch (position) {
        case POSITIONS.ROOT_RIGHT:
          if (!this.rightBar) {
            this.rightBar = util.createPositionBar("xg-right-bar", this.root);
          }
          _root = this.rightBar;
          break;
        case POSITIONS.ROOT_LEFT:
          if (!this.leftBar) {
            this.leftBar = util.createPositionBar("xg-left-bar", this.root);
          }
          _root = this.leftBar;
          break;
        case POSITIONS.ROOT_TOP:
          if (!this.topBar) {
            this.topBar = util.createPositionBar("xg-top-bar", this.root);
            if (this.config.topBarAutoHide) {
              util.addClass(this.topBar, STATE_CLASS.TOP_BAR_AUTOHIDE);
            }
          }
          _root = this.topBar;
          break;
        default:
          _root = this.innerContainer || this.root;
          break;
      }
      return _root;
    }
  }, {
    key: "registerPlugin",
    value: function registerPlugin(plugin, config) {
      var _retPlugin = pluginsManager.formatPluginInfo(plugin, config);
      var PLUFGIN = _retPlugin.PLUFGIN, options = _retPlugin.options;
      var plugins = this.config.plugins;
      var exits = pluginsManager.checkPluginIfExits(PLUFGIN.pluginName, plugins);
      !exits && plugins.push(PLUFGIN);
      var _pConfig = pluginsManager.getRootByConfig(PLUFGIN.pluginName, this.config);
      _pConfig.root && (options.root = _pConfig.root);
      _pConfig.position && (options.position = _pConfig.position);
      var position = options.position ? options.position : options.config && options.config.position || PLUFGIN.defaultConfig && PLUFGIN.defaultConfig.position;
      if (!options.root && typeof position === "string" && position.indexOf("controls") > -1) {
        var _this$controls;
        return (_this$controls = this.controls) === null || _this$controls === void 0 ? void 0 : _this$controls.registerPlugin(PLUFGIN, options, PLUFGIN.pluginName);
      }
      if (!options.root) {
        options.root = this._getRootByPosition(position);
      }
      return pluginsManager.register(this, PLUFGIN, options);
    }
  }, {
    key: "deregister",
    value: function deregister(plugin) {
      if (typeof plugin === "string") {
        pluginsManager.unRegister(this, plugin);
      } else if (plugin instanceof BasePlugin) {
        pluginsManager.unRegister(this, plugin.pluginName);
      }
    }
  }, {
    key: "unRegisterPlugin",
    value: function unRegisterPlugin(plugin) {
      var removedFromConfig = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
      this.deregister(plugin);
      if (removedFromConfig) {
        this.removePluginFromConfig(plugin);
      }
    }
  }, {
    key: "removePluginFromConfig",
    value: function removePluginFromConfig(plugin) {
      var pluginName;
      if (typeof plugin === "string") {
        pluginName = plugin;
      } else if (plugin instanceof BasePlugin) {
        pluginName = plugin.pluginName;
      }
      if (!pluginName) {
        return;
      }
      for (var i = this.config.plugins.length - 1; i > -1; i--) {
        var _plugin = this.config.plugins[i];
        if (_plugin.pluginName.toLowerCase() === pluginName.toLowerCase()) {
          this.config.plugins.splice(i, 1);
          break;
        }
      }
    }
  }, {
    key: "plugins",
    get: function get() {
      return pluginsManager.getPlugins(this);
    }
  }, {
    key: "getPlugin",
    value: function getPlugin(pluginName) {
      var plugin = pluginsManager.findPlugin(this, pluginName);
      return plugin && plugin.pluginName ? plugin : null;
    }
  }, {
    key: "addClass",
    value: function addClass(className) {
      if (!this.root) {
        return;
      }
      if (!util.hasClass(this.root, className)) {
        util.addClass(this.root, className);
      }
    }
  }, {
    key: "removeClass",
    value: function removeClass(className) {
      if (!this.root) {
        return;
      }
      util.removeClass(this.root, className);
    }
  }, {
    key: "hasClass",
    value: function hasClass(className) {
      if (!this.root) {
        return;
      }
      return util.hasClass(this.root, className);
    }
  }, {
    key: "setAttribute",
    value: function setAttribute(key, value) {
      if (!this.root) {
        return;
      }
      this.root.setAttribute(key, value);
    }
  }, {
    key: "removeAttribute",
    value: function removeAttribute(key, value) {
      if (!this.root) {
        return;
      }
      this.root.removeAttribute(key, value);
    }
  }, {
    key: "start",
    value: function start(url) {
      var _this8 = this;
      if (this.state > STATES.ATTACHING) {
        return;
      }
      var playSessionId = this._playSessionId;
      if (util.isNotNull(playSessionId)) {
        this._playSessionId = void 0;
      }
      if (!url && !this.config.url) {
        this.getInitDefinition();
      }
      this.hasStart = true;
      this.setState(STATES.ATTACHING);
      this._registerPlugins(false);
      return pluginsManager.beforeInit(this).then(function() {
        if (!_this8.config) {
          return;
        }
        if (!url) {
          url = _this8.url || _this8.config.url;
        }
        var _furl = _this8.preProcessUrl(url);
        var ret = _this8._startInit(_furl.url, {
          playSessionId
        });
        return ret;
      }).catch(function(e) {
        e.fileName = "player";
        e.lineNumber = "236";
        XG_DEBUG.logError("start:beforeInit:", e);
        throw e;
      });
    }
  }, {
    key: "switchURL",
    value: function switchURL(url, options) {
      var _this9 = this;
      var _src = url;
      if (util.typeOf(url) === "Object") {
        _src = url.url;
      }
      _src = this.preProcessUrl(_src).url;
      var curTime = this.currentTime;
      this.__startTime = curTime;
      var isPaused = this.paused && !this.isError;
      this.src = _src;
      return new Promise(function(resolve, reject) {
        var _error = function _error2(e) {
          _this9.off("timeupdate", _canplay);
          _this9.off("canplay", _canplay);
          reject(e);
        };
        var _canplay = function _canplay2() {
          _this9._seekToStartTime();
          if (isPaused) {
            _this9.pause();
          }
          _this9.off("error", _error);
          resolve(true);
        };
        _this9.once("error", _error);
        if (!_src) {
          _this9.errorHandler("error", {
            code: 6,
            message: "empty_src"
          });
          return;
        }
        if (sniffer.os.isAndroid) {
          _this9.once("timeupdate", _canplay);
        } else {
          _this9.once("canplay", _canplay);
        }
        _this9.play();
      });
    }
  }, {
    key: "videoPlay",
    value: function videoPlay() {
      this.mediaPlay();
    }
  }, {
    key: "mediaPlay",
    value: function mediaPlay() {
      var _this10 = this;
      if (!this.hasStart && this.state < STATES.ATTACHED) {
        this.removeClass(STATE_CLASS.NO_START);
        this.addClass(STATE_CLASS.ENTER);
        this.start();
        this._useAutoplay = true;
        return;
      }
      if (this.state < STATES.RUNNING) {
        this.removeClass(STATE_CLASS.NO_START);
        !this.isCanplay && this.addClass(STATE_CLASS.ENTER);
      }
      var playPromise = _get(_getPrototypeOf(Player2.prototype), "play", this).call(this);
      if (playPromise !== void 0 && playPromise && playPromise.then) {
        playPromise.then(function() {
          _this10.removeClass(STATE_CLASS.NOT_ALLOW_AUTOPLAY);
          _this10.addClass(STATE_CLASS.PLAYING);
          if (_this10.state < STATES.RUNNING) {
            XG_DEBUG.logInfo(">>>>playPromise.then");
            _this10.setState(STATES.RUNNING);
            _this10.emit(AUTOPLAY_STARTED);
          }
        }).catch(function(e) {
          XG_DEBUG.logWarn(">>>>playPromise.catch", e.name);
          if (_this10.media && _this10.media.error) {
            _this10.onError();
            _this10.removeClass(STATE_CLASS.ENTER);
            return;
          }
          if (e.name === "NotAllowedError") {
            _this10._errorTimer = util.setTimeout(_this10, function() {
              _this10._errorTimer = null;
              _this10.emit(AUTOPLAY_PREVENTED);
              _this10.addClass(STATE_CLASS.NOT_ALLOW_AUTOPLAY);
              _this10.removeClass(STATE_CLASS.ENTER);
              _this10.pause();
              _this10.setState(STATES.NOTALLOW);
            }, 0);
          }
        });
      } else {
        XG_DEBUG.logWarn("video.play not return promise");
        if (this.state < STATES.RUNNING) {
          this.setState(STATES.RUNNING);
          this.removeClass(STATE_CLASS.NOT_ALLOW_AUTOPLAY);
          this.removeClass(STATE_CLASS.NO_START);
          this.removeClass(STATE_CLASS.ENTER);
          this.addClass(STATE_CLASS.PLAYING);
          this.emit(AUTOPLAY_STARTED);
        }
      }
      return playPromise;
    }
  }, {
    key: "mediaPause",
    value: function mediaPause() {
      _get(_getPrototypeOf(Player2.prototype), "pause", this).call(this);
    }
  }, {
    key: "videoPause",
    value: function videoPause() {
      _get(_getPrototypeOf(Player2.prototype), "pause", this).call(this);
    }
  }, {
    key: "play",
    value: function play() {
      var _this11 = this;
      this.removeClass(STATE_CLASS.PAUSED);
      return runHooks(this, "play", function() {
        return _this11.mediaPlay();
      });
    }
  }, {
    key: "pause",
    value: function pause() {
      var _this12 = this;
      runHooks(this, "pause", function() {
        _get(_getPrototypeOf(Player2.prototype), "pause", _this12).call(_this12);
      });
    }
  }, {
    key: "seek",
    value: function seek(time, status) {
      var _this13 = this;
      if (!this.media || Number.isNaN(Number(time)) || !this.hasStart) {
        return;
      }
      var _this$config4 = this.config, isSeekedPlay = _this$config4.isSeekedPlay, seekedStatus = _this$config4.seekedStatus;
      var _status = status || (isSeekedPlay ? "play" : seekedStatus);
      time = time < 0 ? 0 : time > this.duration ? parseInt(this.duration, 10) : time;
      !this._isPauseBeforeSeek && (this._isPauseBeforeSeek = this.paused ? 2 : 1);
      this._onceSeekCanplay && this.off(SEEKED, this._onceSeekCanplay);
      this._onceSeekCanplay = function() {
        _this13.removeClass(STATE_CLASS.ENTER);
        _this13.isSeeking = false;
        switch (_status) {
          case "play":
            _this13.play();
            break;
          case "pause":
            _this13.pause();
            break;
          default:
            _this13._isPauseBeforeSeek > 1 || _this13.paused ? _this13.pause() : _this13.play();
        }
        _this13._isPauseBeforeSeek = 0;
        _this13._onceSeekCanplay = null;
      };
      this.once(SEEKED, this._onceSeekCanplay);
      if (this.state < STATES.RUNNING) {
        this.removeClass(STATE_CLASS.NO_START);
        this.currentTime = time;
        this.play();
      } else {
        this.currentTime = time;
      }
    }
  }, {
    key: "getInitDefinition",
    value: function getInitDefinition() {
      var _this14 = this;
      var _this$config5 = this.config, definition = _this$config5.definition, url = _this$config5.url;
      if (!url && definition && definition.list && definition.list.length > 0 && definition.defaultDefinition) {
        definition.list.map(function(item) {
          if (item.definition === definition.defaultDefinition) {
            _this14.config.url = item.url;
            _this14.curDefinition = item;
          }
        });
      }
    }
  }, {
    key: "changeDefinition",
    value: function changeDefinition(to, from) {
      var _this15 = this;
      var definition = this.config.definition;
      if (Array.isArray(definition === null || definition === void 0 ? void 0 : definition.list)) {
        definition.list.forEach(function(item) {
          if ((to === null || to === void 0 ? void 0 : to.definition) === item.definition) {
            _this15.curDefinition = item;
          }
        });
      }
      if (to !== null && to !== void 0 && to.bitrate && typeof to.bitrate !== "number") {
        to.bitrate = parseInt(to.bitrate, 10) || 0;
      }
      this.emit(DEFINITION_CHANGE, {
        from,
        to
      });
      if (!this.hasStart) {
        this.config.url = to.url;
        return;
      }
      var ret = this.switchURL(to.url, _objectSpread2({
        seamless: definition.seamless !== false && typeof MediaSource !== "undefined" && typeof MediaSource.isTypeSupported === "function"
      }, to));
      if (ret && ret.then) {
        ret.then(function() {
          _this15.emit(AFTER_DEFINITION_CHANGE, {
            from,
            to
          });
        });
      } else {
        this.emit(AFTER_DEFINITION_CHANGE, {
          from,
          to
        });
      }
    }
  }, {
    key: "reload",
    value: function reload() {
      this.load();
      this.reloadFunc = function() {
        this.play();
      };
      this.once(LOADED_DATA, this.reloadFunc);
    }
  }, {
    key: "resetState",
    value: function resetState() {
      var _this16 = this;
      var NOT_ALLOW_AUTOPLAY = STATE_CLASS.NOT_ALLOW_AUTOPLAY, PLAYING = STATE_CLASS.PLAYING, NO_START = STATE_CLASS.NO_START, PAUSED = STATE_CLASS.PAUSED, REPLAY2 = STATE_CLASS.REPLAY, ENTER = STATE_CLASS.ENTER, ENDED = STATE_CLASS.ENDED, ERROR2 = STATE_CLASS.ERROR, LOADING2 = STATE_CLASS.LOADING;
      var clsList = [NOT_ALLOW_AUTOPLAY, PLAYING, NO_START, PAUSED, REPLAY2, ENTER, ENDED, ERROR2, LOADING2];
      this.hasStart = false;
      this.isError = false;
      this._useAutoplay = false;
      this.mediaPause();
      this._accPlayed.acc = 0;
      this._accPlayed.t = 0;
      this._accPlayed.loopAcc = 0;
      clsList.forEach(function(cls) {
        _this16.removeClass(cls);
      });
      this.addClass(STATE_CLASS.NO_START);
      this.emit(RESET);
    }
  }, {
    key: "reset",
    value: function reset() {
      var _this17 = this;
      var unregisterPlugins = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : [];
      var isResetConfig = arguments.length > 1 ? arguments[1] : void 0;
      this.resetState();
      var plugins = this.plugins;
      if (!plugins) {
        return;
      }
      unregisterPlugins.map(function(pn) {
        _this17.deregister(pn);
      });
      if (isResetConfig) {
        var de = getDefaultConfig();
        Object.keys(this.config).forEach(function(k) {
          if (_this17.config[k] !== "undefined" && (k === "plugins" || k === "presets" || k === "el" || k === "id")) {
            _this17.config[k] = de[k];
          }
        });
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      var _instManager2, _this18 = this;
      var innerContainer = this.innerContainer, root = this.root, media = this.media;
      if (!root || !media) {
        return;
      }
      this.hasStart = false;
      this._useAutoplay = false;
      root.removeAttribute(PLATER_ID);
      media.removeAttribute(PLATER_ID);
      this.updateAcc("destroy");
      this._unbindEvents();
      this._detachSourceEvents(this.media);
      util.clearAllTimers(this);
      this.emit(DESTROY);
      (_instManager2 = instManager) === null || _instManager2 === void 0 ? void 0 : _instManager2.remove(this);
      pluginsManager.destroy(this);
      delHooksDescriptor(this);
      _get(_getPrototypeOf(Player2.prototype), "destroy", this).call(this);
      if (this.fullscreen && this._fullscreenEl === this.root) {
        this.exitFullscreen();
      }
      if (innerContainer) {
        var _c = innerContainer.children;
        for (var i = 0; i < _c.length; i++) {
          innerContainer.removeChild(_c[i]);
        }
      }
      !innerContainer && media instanceof window.Node && root.contains(media) && !this.config.remainMediaAfterDestroy && root.removeChild(media);
      ["topBar", "leftBar", "rightBar", "innerContainer"].map(function(item) {
        _this18[item] && root.removeChild(_this18[item]);
        _this18[item] = null;
      });
      var cList = root.className.split(" ");
      if (cList.length > 0) {
        root.className = cList.filter(function(name) {
          return name.indexOf("xgplayer") < 0;
        }).join(" ");
      } else {
        root.className = "";
      }
      this.removeAttribute("data-xgfill");
      ["isSeeking", "isCanplay", "isActive", "cssfullscreen", "fullscreen"].forEach(function(key) {
        _this18[key] = false;
      });
    }
  }, {
    key: "replay",
    value: function replay() {
      var _this19 = this;
      this.removeClass(STATE_CLASS.ENDED);
      this.currentTime = 0;
      this.isSeeking = false;
      runHooks(this, "replay", function() {
        _this19.once(SEEKED, function() {
          var playPromise = _this19.mediaPlay();
          if (playPromise && playPromise.catch) {
            playPromise.catch(function(err) {
              console.log(err);
            });
          }
        });
        _this19.emit(REPLAY);
        _this19.onPlay();
      });
    }
  }, {
    key: "retry",
    value: function retry() {
      var _this20 = this;
      this.removeClass(STATE_CLASS.ERROR);
      this.addClass(STATE_CLASS.LOADING);
      runHooks(this, "retry", function() {
        var cur = _this20.currentTime;
        var url = _this20.config.url;
        var _srcRet = !util.isMSE(_this20.media) ? _this20.preProcessUrl(url) : {
          url
        };
        _this20.src = _srcRet.url;
        !_this20.config.isLive && (_this20.currentTime = cur);
        _this20.once(CANPLAY, function() {
          _this20.mediaPlay();
        });
      });
    }
  }, {
    key: "changeFullStyle",
    value: function changeFullStyle(root, el, rootClass, pClassName) {
      if (!root) {
        return;
      }
      if (!pClassName) {
        pClassName = STATE_CLASS.PARENT_FULLSCREEN;
      }
      if (!this._orgCss) {
        this._orgCss = util.filterStyleFromText(root);
      }
      util.addClass(root, rootClass);
      if (el && el !== root && !this._orgPCss) {
        this._orgPCss = util.filterStyleFromText(el);
        util.addClass(el, pClassName);
        el.setAttribute(PLATER_ID, this.playerId);
      }
    }
  }, {
    key: "recoverFullStyle",
    value: function recoverFullStyle(root, el, rootClass, pClassName) {
      if (!pClassName) {
        pClassName = STATE_CLASS.PARENT_FULLSCREEN;
      }
      if (this._orgCss) {
        util.setStyleFromCsstext(root, this._orgCss);
        this._orgCss = "";
      }
      util.removeClass(root, rootClass);
      if (el && el !== root && this._orgPCss) {
        util.setStyleFromCsstext(el, this._orgPCss);
        this._orgPCss = "";
        util.removeClass(el, pClassName);
        el.removeAttribute(PLATER_ID);
      }
    }
  }, {
    key: "getFullscreen",
    value: function getFullscreen() {
      var el = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : this.config.fullscreenTarget;
      var root = this.root, media = this.media;
      if (el === "video" || el === "media") {
        el = this[el];
      }
      if (!el) {
        el = root;
      }
      this._fullScreenOffset = {
        top: util.scrollTop(),
        left: util.scrollLeft()
      };
      this._fullscreenEl = el;
      this._fullActionFrom = "get";
      var fullEl = util.getFullScreenEl();
      if (fullEl === this._fullscreenEl) {
        this.onFullscreenChange();
        return Promise.resolve();
      }
      try {
        for (var i = 0; i < GET_FULLSCREEN_API.length; i++) {
          var key = GET_FULLSCREEN_API[i];
          if (el[key]) {
            var ret = key === "webkitRequestFullscreen" ? el.webkitRequestFullscreen(window.Element.ALLOW_KEYBOARD_INPUT) : el[key]();
            if (ret && ret.then) {
              return ret;
            } else {
              return Promise.resolve();
            }
          }
        }
        if (media.fullscreenEnabled || media.webkitSupportsFullscreen) {
          media.webkitEnterFullscreen();
          return Promise.resolve();
        }
        return Promise.reject(new Error("call getFullscreen fail"));
      } catch (err) {
        return Promise.reject(new Error("call getFullscreen fail"));
      }
    }
  }, {
    key: "exitFullscreen",
    value: function exitFullscreen(el) {
      if (this.isRotateFullscreen) {
        this.exitRotateFullscreen();
      }
      if (!this._fullscreenEl && !util.getFullScreenEl()) {
        return;
      }
      this.root;
      var media = this.media;
      this._fullActionFrom = "exit";
      try {
        for (var i = 0; i < EXIT_FULLSCREEN_API.length; i++) {
          var key = EXIT_FULLSCREEN_API[i];
          if (document[key]) {
            var ret = document[key]();
            if (ret && ret.then) {
              return ret;
            } else {
              return Promise.resolve();
            }
          }
        }
        if (media && media.webkitSupportsFullscreen) {
          media.webkitExitFullScreen();
          return Promise.resolve();
        }
        return Promise.reject(new Error("call exitFullscreen fail"));
      } catch (err) {
        return Promise.reject(new Error("call exitFullscreen fail"));
      }
    }
  }, {
    key: "getCssFullscreen",
    value: function getCssFullscreen() {
      var el = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : this.config.fullscreenTarget;
      if (this.isRotateFullscreen) {
        this.exitRotateFullscreen();
      } else if (this.fullscreen) {
        this.exitFullscreen();
      }
      var _class = el ? "".concat(STATE_CLASS.INNER_FULLSCREEN, " ").concat(STATE_CLASS.CSS_FULLSCREEN) : STATE_CLASS.CSS_FULLSCREEN;
      this.changeFullStyle(this.root, el, _class);
      var _this$config$fullscre = this.config.fullscreen, fullscreen = _this$config$fullscre === void 0 ? {} : _this$config$fullscre;
      var useCssFullscreen = fullscreen.useCssFullscreen === true || typeof fullscreen.useCssFullscreen === "function" && fullscreen.useCssFullscreen();
      if (useCssFullscreen) {
        this.fullscreen = true;
        this.emit(FULLSCREEN_CHANGE, true);
      }
      this._cssfullscreenEl = el;
      this.cssfullscreen = true;
      this.emit(CSS_FULLSCREEN_CHANGE, true);
    }
  }, {
    key: "exitCssFullscreen",
    value: function exitCssFullscreen() {
      var _class = this._cssfullscreenEl ? "".concat(STATE_CLASS.INNER_FULLSCREEN, " ").concat(STATE_CLASS.CSS_FULLSCREEN) : STATE_CLASS.CSS_FULLSCREEN;
      if (!this.fullscreen) {
        this.recoverFullStyle(this.root, this._cssfullscreenEl, _class);
      } else {
        var _this$config$fullscre2 = this.config.fullscreen, fullscreen = _this$config$fullscre2 === void 0 ? {} : _this$config$fullscre2;
        var useCssFullscreen = fullscreen.useCssFullscreen === true || typeof fullscreen.useCssFullscreen === "function" && fullscreen.useCssFullscreen();
        if (useCssFullscreen) {
          this.recoverFullStyle(this.root, this._cssfullscreenEl, _class);
          this.fullscreen = false;
          this.emit(FULLSCREEN_CHANGE, false);
        } else {
          this.removeClass(_class);
        }
      }
      this._cssfullscreenEl = null;
      this.cssfullscreen = false;
      this.emit(CSS_FULLSCREEN_CHANGE, false);
    }
  }, {
    key: "getRotateFullscreen",
    value: function getRotateFullscreen() {
      var el = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : this.config.fullscreenTarget;
      if (this.cssfullscreen) {
        this.exitCssFullscreen(el);
      }
      var _class = el ? "".concat(STATE_CLASS.INNER_FULLSCREEN, " ").concat(STATE_CLASS.ROTATE_FULLSCREEN) : STATE_CLASS.ROTATE_FULLSCREEN;
      this._fullscreenEl = el || this.root;
      this.changeFullStyle(this.root, el, _class, STATE_CLASS.PARENT_ROTATE_FULLSCREEN);
      this.isRotateFullscreen = true;
      this.fullscreen = true;
      this.setRotateDeg(90);
      this._rootStyle = this.root.getAttribute("style");
      var isRotate90 = Math.abs(window.orientation) === 90 || Math.abs(screen.orientation.angle) === 90;
      this.root.style.width = isRotate90 ? "".concat(window.innerWidth, "px") : "".concat(window.innerHeight, "px");
      this.emit(FULLSCREEN_CHANGE, true);
    }
  }, {
    key: "exitRotateFullscreen",
    value: function exitRotateFullscreen(el) {
      var _class = this._fullscreenEl !== this.root ? "".concat(STATE_CLASS.INNER_FULLSCREEN, " ").concat(STATE_CLASS.ROTATE_FULLSCREEN) : STATE_CLASS.ROTATE_FULLSCREEN;
      this.recoverFullStyle(this.root, this._fullscreenEl, _class, STATE_CLASS.PARENT_ROTATE_FULLSCREEN);
      this.isRotateFullscreen = false;
      this.fullscreen = false;
      this.setRotateDeg(0);
      this.emit(FULLSCREEN_CHANGE, false);
      if (this._rootStyle) {
        this.root.style.style = this._rootStyle;
        this._rootStyle = false;
      }
    }
  }, {
    key: "setRotateDeg",
    value: function setRotateDeg(deg) {
      if (window.orientation === 90 || window.orientation === -90) {
        this.rotateDeg = 0;
      } else {
        this.rotateDeg = deg;
      }
    }
  }, {
    key: "focus",
    value: function focus() {
      var data = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {
        autoHide: !this.config.closeDelayBlur,
        delay: this.config.inactive
      };
      if (this.isActive) {
        this.onFocus(data);
        return;
      }
      this.emit(PLAYER_FOCUS, _objectSpread2({
        paused: this.paused,
        ended: this.ended
      }, data));
    }
  }, {
    key: "blur",
    value: function blur() {
      var data = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {
        ignorePaused: false
      };
      if (!this.isActive) {
        this.onBlur(data);
        return;
      }
      this._clearUserTimer();
      this.emit(PLAYER_BLUR, _objectSpread2({
        paused: this.paused,
        ended: this.ended
      }, data));
    }
  }, {
    key: "onFocus",
    value: function onFocus() {
      var _this21 = this;
      var data = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {
        autoHide: true,
        delay: 3e3
      };
      var innerStates = this.innerStates;
      this.isActive = true;
      this.removeClass(STATE_CLASS.INACTIVE);
      this._clearUserTimer();
      if (data.isLock !== void 0) {
        innerStates.isActiveLocked = data.isLock;
      }
      if (data.autoHide === false || data.isLock === true || innerStates.isActiveLocked) {
        this._clearUserTimer();
        return;
      }
      var time = data && data.delay ? data.delay : this.config.inactive;
      this.userTimer = util.setTimeout(this, function() {
        _this21.userTimer = null;
        _this21.blur();
      }, time);
    }
  }, {
    key: "onBlur",
    value: function onBlur() {
      var _ref = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {}, _ref$ignorePaused = _ref.ignorePaused, ignorePaused = _ref$ignorePaused === void 0 ? false : _ref$ignorePaused;
      if (this.innerStates.isActiveLocked) {
        return;
      }
      var closePauseVideoFocus = this.config.closePauseVideoFocus;
      this.isActive = false;
      if (ignorePaused || closePauseVideoFocus || !this.paused && !this.ended) {
        this.addClass(STATE_CLASS.INACTIVE);
      }
    }
  }, {
    key: "onEmptied",
    value: function onEmptied() {
      this.updateAcc("emptied");
    }
  }, {
    key: "onCanplay",
    value: function onCanplay() {
      this.removeClass(STATE_CLASS.ENTER);
      this.removeClass(STATE_CLASS.ERROR);
      this.removeClass(STATE_CLASS.LOADING);
      this.isCanplay = true;
      this.waitTimer && util.clearTimeout(this, this.waitTimer);
    }
  }, {
    key: "onLoadeddata",
    value: function onLoadeddata() {
      var _this22 = this;
      this.isError = false;
      this.isSeeking = false;
      if (this.__startTime > 0) {
        if (this.duration > 0) {
          this._seekToStartTime();
        } else {
          this.once(DURATION_CHANGE, function() {
            _this22._seekToStartTime();
          });
        }
      }
    }
  }, {
    key: "onLoadstart",
    value: function onLoadstart() {
      this.removeClass(STATE_CLASS.ERROR);
      this.isCanplay = false;
    }
  }, {
    key: "onPlay",
    value: function onPlay() {
      if (this.state === STATES.ENDED) {
        this.setState(STATES.RUNNING);
      }
      this.removeClass(STATE_CLASS.PAUSED);
      this.ended && this.removeClass(STATE_CLASS.ENDED);
      !this.config.closePlayVideoFocus && this.focus();
    }
  }, {
    key: "onPause",
    value: function onPause() {
      this.addClass(STATE_CLASS.PAUSED);
      this.updateAcc("pause");
      if (!this.config.closePauseVideoFocus) {
        this._clearUserTimer();
        this.focus();
      }
    }
  }, {
    key: "onEnded",
    value: function onEnded() {
      this.updateAcc("ended");
      this.addClass(STATE_CLASS.ENDED);
      this.setState(STATES.ENDED);
    }
  }, {
    key: "onError",
    value: function onError() {
      this.isError = true;
      this.updateAcc("error");
      this.removeClass(STATE_CLASS.NOT_ALLOW_AUTOPLAY);
      this.removeClass(STATE_CLASS.NO_START);
      this.removeClass(STATE_CLASS.ENTER);
      this.removeClass(STATE_CLASS.LOADING);
      this.addClass(STATE_CLASS.ERROR);
    }
  }, {
    key: "onSeeking",
    value: function onSeeking() {
      if (!this.isSeeking) {
        this.updateAcc("seeking");
      }
      this.isSeeking = true;
      this.addClass(STATE_CLASS.SEEKING);
    }
  }, {
    key: "onSeeked",
    value: function onSeeked() {
      this.isSeeking = false;
      if (this.waitTimer) {
        util.clearTimeout(this, this.waitTimer);
      }
      this.removeClass(STATE_CLASS.LOADING);
      this.removeClass(STATE_CLASS.SEEKING);
    }
  }, {
    key: "onWaiting",
    value: function onWaiting() {
      var _this23 = this;
      if (this.waitTimer) {
        util.clearTimeout(this, this.waitTimer);
      }
      this.updateAcc("waiting");
      this.waitTimer = util.setTimeout(this, function() {
        _this23.addClass(STATE_CLASS.LOADING);
        _this23.emit(LOADING);
        util.clearTimeout(_this23, _this23.waitTimer);
        _this23.waitTimer = null;
      }, this.config.minWaitDelay);
    }
  }, {
    key: "onPlaying",
    value: function onPlaying() {
      var _this24 = this;
      this.isError = false;
      var NO_START = STATE_CLASS.NO_START, PAUSED = STATE_CLASS.PAUSED, ENDED = STATE_CLASS.ENDED, ERROR2 = STATE_CLASS.ERROR, REPLAY2 = STATE_CLASS.REPLAY, LOADING2 = STATE_CLASS.LOADING;
      var clsList = [NO_START, PAUSED, ENDED, ERROR2, REPLAY2, LOADING2];
      clsList.forEach(function(cls) {
        _this24.removeClass(cls);
      });
      if (!this._accPlayed.t && !this.paused && !this.ended) {
        this._accPlayed.t = new Date().getTime();
      }
    }
  }, {
    key: "onTimeupdate",
    value: function onTimeupdate() {
      !this._videoHeight && this.media.videoHeight && this.resize();
      if ((this.waitTimer || this.hasClass(STATE_CLASS.LOADING)) && this.media.readyState > 2) {
        this.removeClass(STATE_CLASS.LOADING);
        util.clearTimeout(this, this.waitTimer);
        this.waitTimer = null;
      }
      if (!this.paused && this.state === STATES.NOTALLOW && this.duration) {
        this.setState(STATES.RUNNING);
        this.emit(AUTOPLAY_STARTED);
      }
      if (!this._accPlayed.t && !this.paused && !this.ended) {
        this._accPlayed.t = new Date().getTime();
      }
    }
  }, {
    key: "onVolumechange",
    value: function onVolumechange() {
      util.typeOf(this.config.volume) === "Number" && (this.config.volume = this.volume);
    }
  }, {
    key: "onRatechange",
    value: function onRatechange() {
      this.config.defaultPlaybackRate = this.playbackRate;
    }
  }, {
    key: "emitUserAction",
    value: function emitUserAction(event, action, params) {
      if (!this.media || !action || !event) {
        return;
      }
      var eventType = util.typeOf(event) === "String" ? event : event.type || "";
      if (params.props && util.typeOf(params.props) !== "Array") {
        params.props = [params.props];
      }
      this.emit(USER_ACTION, _objectSpread2({
        eventType,
        action,
        currentTime: this.currentTime,
        duration: this.duration,
        ended: this.ended,
        event
      }, params));
    }
  }, {
    key: "updateAcc",
    value: function updateAcc(endType) {
      if (this._accPlayed.t) {
        var _at = new Date().getTime() - this._accPlayed.t;
        this._accPlayed.acc += _at;
        this._accPlayed.t = 0;
        if (endType === "ended" || this.ended) {
          this._accPlayed.loopAcc = this._accPlayed.acc;
        }
      }
    }
  }, {
    key: "checkBuffer",
    value: function checkBuffer(time) {
      var options = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {
        startDiff: 0,
        endDiff: 0
      };
      var _ref2 = options || {}, _ref2$startDiff = _ref2.startDiff, startDiff = _ref2$startDiff === void 0 ? 0 : _ref2$startDiff, _ref2$endDiff = _ref2.endDiff, endDiff = _ref2$endDiff === void 0 ? 0 : _ref2$endDiff;
      var buffered = this.media.buffered;
      if (!buffered || buffered.length === 0 || !this.duration) {
        return true;
      }
      var currentTime = time || this.media.currentTime || 0.2;
      var len = buffered.length;
      for (var i = 0; i < len; i++) {
        if (buffered.start(i) + startDiff <= currentTime && buffered.end(i) - endDiff > currentTime) {
          return true;
        }
      }
      return false;
    }
  }, {
    key: "resizePosition",
    value: function resizePosition() {
      var _this25 = this;
      var _this$videoPos = this.videoPos, vy = _this$videoPos.vy, vx = _this$videoPos.vx, h = _this$videoPos.h, w = _this$videoPos.w;
      var rotate = this.videoPos.rotate;
      if (rotate < 0 && h < 0 && w < 0) {
        return;
      }
      var _pi = this.videoPos._pi;
      if (!_pi && this.media.videoHeight) {
        _pi = this.media.videoWidth / this.media.videoHeight * 100;
      }
      if (!_pi) {
        return;
      }
      this.videoPos.pi = _pi;
      rotate = rotate < 0 ? 0 : rotate;
      var _pos = {
        rotate
      };
      var offsetY = 0;
      var offsetX = 0;
      var scale = 1;
      var _t = Math.abs(rotate / 90);
      var root = this.root, innerContainer = this.innerContainer;
      var width = root.offsetWidth;
      var height = innerContainer ? innerContainer.offsetHeight : root.offsetHeight;
      var styles = {};
      var rHeight = height;
      var rWidth = width;
      if (_t % 2 === 0) {
        scale = h > 0 ? 100 / h : w > 0 ? 100 / w : 1;
        _pos.scale = scale;
        offsetY = vy > 0 ? (100 - h) / 2 - vy : 0;
        _pos.y = _t === 2 ? 0 - offsetY : offsetY;
        offsetX = vx > 0 ? (100 - w) / 2 - vx : 0;
        _pos.x = _t === 2 ? 0 - offsetX : offsetX;
        styles.width = "".concat(rWidth, "px");
        styles.height = "".concat(rHeight, "px");
        styles.maxWidth = "";
        styles.maxHeight = "";
      } else if (_t % 2 === 1) {
        rWidth = height;
        rHeight = width;
        var offset = height - width;
        offsetX = -offset / 2 / rWidth * 100;
        _pos.x = _t === 3 ? offsetX + vy / 2 : offsetX - vy / 2;
        offsetY = offset / 2 / rHeight * 100;
        _pos.y = _t === 3 ? offsetY + vx / 2 : offsetY - vx / 2;
        _pos.scale = scale;
        styles.width = "".concat(rWidth, "px");
        styles.maxWidth = "".concat(rWidth, "px");
        styles.height = "".concat(rHeight, "px");
        styles.maxHeight = "".concat(rHeight, "px");
      }
      var formStyle = util.getTransformStyle(_pos, this.media.style.transform || this.media.style.webkitTransform);
      styles.transform = formStyle;
      styles.webkitTransform = formStyle;
      Object.keys(styles).map(function(key) {
        _this25.media.style[key] = styles[key];
      });
    }
  }, {
    key: "position",
    value: function position() {
      var pos = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : {
        h: 0,
        y: 0,
        x: 0,
        w: 0
      };
      if (!this.media || !pos || !pos.h) {
        return;
      }
      var videoPos = this.videoPos;
      videoPos.h = pos.h * 100 || 0;
      videoPos.w = pos.w * 100 || 0;
      videoPos.vx = pos.x * 100 || 0;
      videoPos.vy = pos.y * 100 || 0;
      this.resizePosition();
    }
  }, {
    key: "setConfig",
    value: function setConfig(config) {
      var _this26 = this;
      if (!config) {
        return;
      }
      Object.keys(config).map(function(key) {
        if (key !== "plugins") {
          _this26.config[key] = config[key];
          var plugin = _this26.plugins[key.toLowerCase()];
          if (plugin && util.typeOf(plugin.setConfig) === "Function") {
            plugin.setConfig(config[key]);
          }
        }
      });
    }
  }, {
    key: "playNext",
    value: function playNext(config) {
      var _this27 = this;
      this.resetState();
      this.setConfig(config);
      this._currentTime = 0;
      this._duration = 0;
      runHooks(this, "playnext", function() {
        _this27.start();
        _this27.emit(PLAYNEXT, config);
      });
    }
  }, {
    key: "resize",
    value: function resize() {
      var _this28 = this;
      if (!this.media || !this.root) {
        return;
      }
      var containerSize = this.root.getBoundingClientRect();
      this.sizeInfo.width = containerSize.width;
      this.sizeInfo.height = containerSize.height;
      this.sizeInfo.left = containerSize.left;
      this.sizeInfo.top = containerSize.top;
      var _this$media = this.media, videoWidth = _this$media.videoWidth, videoHeight = _this$media.videoHeight;
      var _this$config6 = this.config, fitVideoSize = _this$config6.fitVideoSize, videoFillMode = _this$config6.videoFillMode;
      if (videoFillMode === "fill" || videoFillMode === "cover" || videoFillMode === "contain") {
        this.setAttribute("data-xgfill", videoFillMode);
      }
      if (!videoHeight || !videoWidth) {
        return;
      }
      this._videoHeight = videoHeight;
      this._videoWidth = videoWidth;
      var controlsHeight = this.controls && this.innerContainer ? this.controls.root.getBoundingClientRect().height : 0;
      var width = containerSize.width;
      var height = containerSize.height - controlsHeight;
      var videoFit = parseInt(videoWidth / videoHeight * 1e3, 10);
      var fit = parseInt(width / height * 1e3, 10);
      var rWidth = width;
      var rHeight = height;
      var _style = {};
      if (fitVideoSize === "auto" && fit > videoFit || fitVideoSize === "fixWidth") {
        rHeight = width / videoFit * 1e3;
        if (this.config.fluid) {
          _style.paddingTop = "".concat(rHeight * 100 / rWidth, "%");
        } else {
          _style.height = "".concat(rHeight + controlsHeight, "px");
        }
      } else if (fitVideoSize === "auto" && fit < videoFit || fitVideoSize === "fixHeight") {
        rWidth = videoFit * height / 1e3;
        _style.width = "".concat(rWidth, "px");
      }
      if (!this.fullscreen && !this.cssfullscreen) {
        Object.keys(_style).forEach(function(key) {
          _this28.root.style[key] = _style[key];
        });
      }
      if (videoFillMode === "fillHeight" && fit < videoFit || videoFillMode === "fillWidth" && fit > videoFit) {
        this.setAttribute("data-xgfill", "cover");
      }
      var data = {
        videoScale: videoFit,
        vWidth: rWidth,
        vHeight: rHeight,
        cWidth: rWidth,
        cHeight: rHeight + controlsHeight
      };
      this.resizePosition();
      this.emit(VIDEO_RESIZE, data);
    }
  }, {
    key: "updateObjectPosition",
    value: function updateObjectPosition() {
      var left = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : 0;
      var top = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : 0;
      if (this.media.updateObjectPosition) {
        this.media.updateObjectPosition(left, top);
        return;
      }
      this.media.style.objectPosition = "".concat(left * 100, "% ").concat(top * 100, "%");
    }
  }, {
    key: "setState",
    value: function setState(newState) {
      XG_DEBUG.logInfo("setState", "state from:".concat(STATE_ARRAY[this.state], " to:").concat(STATE_ARRAY[newState]));
      this._state = newState;
    }
  }, {
    key: "preProcessUrl",
    value: function preProcessUrl(url, ext) {
      var _this$config7 = this.config, preProcessUrl2 = _this$config7.preProcessUrl, preProcessUrlOptions = _this$config7.preProcessUrlOptions;
      var processUrlOptions = Object.assign({}, preProcessUrlOptions, ext);
      return !util.isBlob(url) && typeof preProcessUrl2 === "function" ? preProcessUrl2(url, processUrlOptions) : {
        url
      };
    }
  }, {
    key: "_seekToStartTime",
    value: function _seekToStartTime() {
      if (this.__startTime > 0 && this.duration > 0) {
        this.currentTime = this.__startTime > this.duration ? this.duration : this.__startTime;
        this.__startTime = -1;
      }
    }
  }, {
    key: "state",
    get: function get() {
      return this._state;
    }
  }, {
    key: "isFullscreen",
    get: function get() {
      return this.fullscreen;
    }
  }, {
    key: "isCssfullScreen",
    get: function get() {
      return this.cssfullscreen;
    }
  }, {
    key: "hasStart",
    get: function get() {
      return this._hasStart;
    },
    set: function set(bool) {
      if (typeof bool === "boolean") {
        this._hasStart = bool;
        if (bool === false) {
          this.setState(STATES.READY);
        }
        this.emit("hasstart");
      }
    }
  }, {
    key: "isPlaying",
    get: function get() {
      return this._state === STATES.RUNNING || this._state === STATES.ENDED;
    },
    set: function set(value) {
      if (value) {
        this.setState(STATES.RUNNING);
      } else {
        this._state >= STATES.RUNNING && this.setState(STATES.ATTACHED);
      }
    }
  }, {
    key: "definitionList",
    get: function get() {
      if (!this.config || !this.config.definition) {
        return [];
      }
      return this.config.definition.list || [];
    },
    set: function set(list) {
      var _this29 = this;
      var definition = this.config.definition;
      var curDef = null;
      var targetDef = null;
      definition.list = list;
      this.emit("resourceReady", list);
      list.forEach(function(item) {
        var _this29$curDefinition;
        if (((_this29$curDefinition = _this29.curDefinition) === null || _this29$curDefinition === void 0 ? void 0 : _this29$curDefinition.definition) === item.definition) {
          curDef = item;
        }
        if (definition.defaultDefinition === item.definition) {
          targetDef = item;
        }
      });
      if (!targetDef && list.length > 0) {
        targetDef = list[0];
      }
      curDef ? this.changeDefinition(curDef) : targetDef && this.changeDefinition(targetDef);
    }
  }, {
    key: "videoFrameInfo",
    get: function get() {
      var ret = {
        total: 0,
        dropped: 0,
        corrupted: 0,
        droppedRate: 0,
        droppedDuration: 0
      };
      if (!this.media || !this.media.getVideoPlaybackQuality) {
        return ret;
      }
      var _quality = this.media.getVideoPlaybackQuality();
      ret.dropped = _quality.droppedVideoFrames || 0;
      ret.total = _quality.totalVideoFrames || 0;
      ret.corrupted = _quality.corruptedVideoFrames || 0;
      if (ret.total > 0) {
        ret.droppedRate = ret.dropped / ret.total * 100;
        ret.droppedDuration = parseInt(this.cumulateTime / ret.total * ret.dropped, 0);
      }
      return ret;
    }
  }, {
    key: "lang",
    get: function get() {
      return this.config.lang;
    },
    set: function set(lang) {
      var result = I18N.langKeys.filter(function(key) {
        return key === lang;
      });
      if (result.length === 0 && lang !== "zh") {
        console.error("Sorry, set lang fail, because the language [".concat(lang, "] is not supported now, list of all supported languages is [").concat(I18N.langKeys.join(), "] "));
        return;
      }
      this.config.lang = lang;
      pluginsManager.setLang(lang, this);
    }
  }, {
    key: "i18n",
    get: function get() {
      var _l = this.config.lang;
      if (_l === "zh") {
        _l = "zh-cn";
      }
      return this.__i18n.lang[_l] || this.__i18n.lang.en;
    }
  }, {
    key: "i18nKeys",
    get: function get() {
      return this.__i18n.textKeys || {};
    }
  }, {
    key: "version",
    get: function get() {
      return version;
    }
  }, {
    key: "playerId",
    get: function get() {
      return this._pluginInfoId;
    }
  }, {
    key: "url",
    get: function get() {
      return this.__url || this.config.url;
    },
    set: function set(url) {
      this.__url = url;
    }
  }, {
    key: "poster",
    get: function get() {
      return this.plugins.poster ? this.plugins.poster.config.poster : this.config.poster;
    },
    set: function set(posterUrl) {
      this.plugins.poster && this.plugins.poster.update(posterUrl);
    }
  }, {
    key: "readyState",
    get: function get() {
      return _get(_getPrototypeOf(Player2.prototype), "readyState", this);
    }
  }, {
    key: "error",
    get: function get() {
      var key = _get(_getPrototypeOf(Player2.prototype), "error", this);
      return this.i18n[key] || key;
    }
  }, {
    key: "networkState",
    get: function get() {
      return _get(_getPrototypeOf(Player2.prototype), "networkState", this);
    }
  }, {
    key: "fullscreenChanging",
    get: function get() {
      return !(this._fullScreenOffset === null);
    }
  }, {
    key: "cumulateTime",
    get: function get() {
      var _this$_accPlayed = this._accPlayed, acc = _this$_accPlayed.acc, t = _this$_accPlayed.t;
      return t ? new Date().getTime() - t + acc : acc;
    }
  }, {
    key: "zoom",
    get: function get() {
      return this.config.zoom;
    },
    set: function set(value) {
      this.config.zoom = value;
    }
  }, {
    key: "videoRotateDeg",
    get: function get() {
      return this.videoPos.rotate;
    },
    set: function set(val) {
      val = util.convertDeg(val);
      if (val % 90 !== 0 || val === this.videoPos.rotate) {
        return;
      }
      this.videoPos.rotate = val;
      this.resizePosition();
    }
  }, {
    key: "avgSpeed",
    get: function get() {
      return AVG_SPEED;
    },
    set: function set(val) {
      AVG_SPEED = val;
    }
  }, {
    key: "realTimeSpeed",
    get: function get() {
      return REAL_TIME_SPEED;
    },
    set: function set(val) {
      REAL_TIME_SPEED = val;
    }
  }, {
    key: "offsetCurrentTime",
    get: function get() {
      return this._offsetInfo.currentTime || 0;
    },
    set: function set(val) {
      this._offsetInfo.currentTime = val;
    }
  }, {
    key: "offsetDuration",
    get: function get() {
      return this._offsetInfo.duration || 0;
    },
    set: function set(val) {
      this._offsetInfo.duration = val || 0;
    }
  }, {
    key: "hook",
    value: function hook$1(hookName, handler) {
      return hook.call.apply(hook, [this].concat(Array.prototype.slice.call(arguments)));
    }
  }, {
    key: "useHooks",
    value: function useHooks$1(hookName, handler) {
      return useHooks.call.apply(useHooks, [this].concat(Array.prototype.slice.call(arguments)));
    }
  }, {
    key: "removeHooks",
    value: function removeHooks$1(hookName, handler) {
      return removeHooks.call.apply(removeHooks, [this].concat(Array.prototype.slice.call(arguments)));
    }
  }, {
    key: "usePluginHooks",
    value: function usePluginHooks$1(pluginName, hookName, handler) {
      for (var _len = arguments.length, args = new Array(_len > 3 ? _len - 3 : 0), _key = 3; _key < _len; _key++) {
        args[_key - 3] = arguments[_key];
      }
      return usePluginHooks.call.apply(usePluginHooks, [this].concat(Array.prototype.slice.call(arguments)));
    }
  }, {
    key: "removePluginHooks",
    value: function removePluginHooks$1(pluginName, hookName, handler) {
      for (var _len2 = arguments.length, args = new Array(_len2 > 3 ? _len2 - 3 : 0), _key2 = 3; _key2 < _len2; _key2++) {
        args[_key2 - 3] = arguments[_key2];
      }
      return removePluginHooks.call.apply(removePluginHooks, [this].concat(Array.prototype.slice.call(arguments)));
    }
  }, {
    key: "setUserActive",
    value: function setUserActive(isActive, isMuted) {
      var _instManager3;
      if (typeof isMuted === "boolean" && isMuted !== this.muted) {
        this.addInnerOP("volumechange");
        if (util.typeOf(isMuted) === Boolean) {
          this.muted = isMuted;
        }
      }
      (_instManager3 = instManager) === null || _instManager3 === void 0 ? void 0 : _instManager3.setActive(this.playerId, isActive);
    }
  }], [{
    key: "debugger",
    get: function get() {
      return XG_DEBUG.config.debug;
    },
    set: function set(value) {
      XG_DEBUG.config.debug = value;
    }
  }, {
    key: "instManager",
    get: function get() {
      return instManager;
    },
    set: function set(value) {
      instManager = value;
    }
  }, {
    key: "getCurrentUserActivePlayerId",
    value: function getCurrentUserActivePlayerId() {
      var _instManager4;
      return (_instManager4 = instManager) === null || _instManager4 === void 0 ? void 0 : _instManager4.getActiveId();
    }
  }, {
    key: "setCurrentUserActive",
    value: function setCurrentUserActive(playerId, isActive) {
      var _instManager5;
      (_instManager5 = instManager) === null || _instManager5 === void 0 ? void 0 : _instManager5.setActive(playerId, isActive);
    }
  }, {
    key: "isHevcSupported",
    value: function isHevcSupported() {
      return sniffer.isHevcSupported();
    }
  }, {
    key: "probeConfigSupported",
    value: function probeConfigSupported(info) {
      return sniffer.probeConfigSupported(info);
    }
  }, {
    key: "install",
    value: function install(name, descriptor) {
      if (!Player2.plugins) {
        Player2.plugins = {};
      }
      if (!Player2.plugins[name]) {
        Player2.plugins[name] = descriptor;
      }
    }
  }, {
    key: "use",
    value: function use(name, descriptor) {
      if (!Player2.plugins) {
        Player2.plugins = {};
      }
      Player2.plugins[name] = descriptor;
    }
  }]);
  return Player2;
}(MediaProxy);
_defineProperty(Player, "defaultPreset", null);
_defineProperty(Player, "XgVideoProxy", null);
Player.instManager = InstManager.getInstance();
export { Player as default };
