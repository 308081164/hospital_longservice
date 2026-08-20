import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck } from "../../_virtual/_rollupPluginBabelHelpers.js";
import DanmuJs from "danmu.js";
import util from "../../utils/util.js";
import { TIME_UPDATE, PAUSE, PLAY, SEEKING, VIDEO_RESIZE, SEEKED } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
import DanmuPanel from "./danmuPanel.js";
export { default as DanmuPanel } from "./danmuPanel.js";
import DanmuIcon from "./danmuIcon.js";
export { default as DanmuIcon } from "./danmuIcon.js";
var MIN_INTERVAL = 300;
var Danmu = /* @__PURE__ */ function(_Plugin) {
  _inherits(Danmu2, _Plugin);
  var _super = _createSuper(Danmu2);
  function Danmu2(args) {
    var _this;
    _classCallCheck(this, Danmu2);
    _this = _super.call(this, args);
    _this.danmujs = null;
    _this.danmuPanel = null;
    _this.isOpen = false;
    _this.seekCost = 0;
    _this.intervalId = 0;
    _this.isUseClose = false;
    return _this;
  }
  _createClass(Danmu2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      if (this.playerConfig.isLive) {
        this.config.isLive = true;
      }
      this.initDanmu();
      this.registerExtIcons();
      this.once(TIME_UPDATE, function() {
        _this2.config.defaultOpen && !_this2.isUseClose && _this2.start();
      });
      this.on(PAUSE, function() {
        _this2.isOpen && _this2.danmujs && _this2.danmujs.pause();
      });
      this.on(PLAY, function() {
        _this2.isOpen && _this2.danmujs && _this2.danmujs.play();
      });
      this.on(SEEKING, function() {
        _this2.seekCost = window.performance.now();
        !_this2.config.isLive && _this2.danmujs && _this2.danmujs.stop();
      });
      this.on(VIDEO_RESIZE, function() {
        _this2.resize();
      });
      this.on(SEEKED, function() {
        if (!_this2.danmujs || !_this2.isOpen) {
          return;
        }
        if (_this2.intervalId) {
          util.clearTimeout(_this2, _this2.intervalId);
          _this2.intervalId = null;
        }
        var now = window.performance.now();
        var delayTime = now - _this2.seekCost > MIN_INTERVAL ? 100 : MIN_INTERVAL;
        _this2.intervalId = util.setTimeout(_this2, function() {
          _this2.danmujs.start();
          _this2.intervalId = null;
          if (_this2.player.paused) {
            _this2.danmujs.pause();
          }
        }, delayTime);
      });
    }
  }, {
    key: "onPluginsReady",
    value: function onPluginsReady() {
      var pcPlugin = this.player.plugins.pc;
      if (pcPlugin) {
        pcPlugin.onVideoDblClick && this.bind("dblclick", pcPlugin.onVideoDblClick);
        pcPlugin.onVideoClick && this.bind("click", pcPlugin.onVideoClick);
      }
    }
  }, {
    key: "initDanmu",
    value: function initDanmu() {
      var player = this.player, config = this.config;
      var _this$config = this.config, channelSize = _this$config.channelSize, fontSize = _this$config.fontSize, opacity = _this$config.opacity, mouseControl = _this$config.mouseControl, mouseControlPause = _this$config.mouseControlPause, area = _this$config.area, defaultOff = _this$config.defaultOff;
      var danmuConfig = {
        container: this.root,
        player: player.media,
        comments: this.config.comments,
        live: config.isLive,
        defaultOff,
        area,
        mouseControl,
        mouseControlPause
      };
      if (config.ext) {
        Object.keys(config.ext).map(function(key) {
          danmuConfig[key] = config.ext[key];
        });
      }
      var danmu = new DanmuJs(danmuConfig);
      this.danmujs = danmu;
      player.danmu = danmu;
      this.setFontSize(fontSize, channelSize);
      this.setArea(area);
      this.resize();
      opacity !== 1 && this.setOpacity(opacity);
    }
  }, {
    key: "registerExtIcons",
    value: function registerExtIcons() {
      var _this3 = this;
      var player = this.player, config = this.config;
      if (config.panel && player.controls) {
        var panelOptions = {
          config: {
            onChangeset: function onChangeset(set) {
              _this3.changeSet(set);
            }
          }
        };
        this.danmuPanel = player.controls.registerPlugin(DanmuPanel, panelOptions, DanmuPanel.pluginName);
      }
      var switchConfig = config.switchConfig;
      if (!config.closeDefaultBtn && player.controls) {
        var buttonOptions = {
          config: {
            onSwitch: function onSwitch(event, isOpen) {
              _this3.onSwitch(event, isOpen);
            }
          }
        };
        Object.keys(switchConfig).map(function(key) {
          buttonOptions.config[key] = switchConfig[key];
        });
        this.danmuButton = player.controls.registerPlugin(DanmuIcon, buttonOptions, DanmuIcon.pluginName);
        this.config.defaultOpen && this.danmuButton.switchState(true);
      }
    }
  }, {
    key: "changeSet",
    value: function changeSet(set) {
    }
  }, {
    key: "onSwitch",
    value: function onSwitch(event, defaultOpen) {
      this.emitUserAction(event, "switch_danmu", {
        prop: "isOpen",
        from: !defaultOpen,
        to: defaultOpen
      });
      if (defaultOpen) {
        this.start();
      } else {
        this.stop();
      }
    }
  }, {
    key: "start",
    value: function start() {
      var _this4 = this;
      if (this.isOpen || !this.danmujs) {
        return;
      }
      this.isUseClose = false;
      this.show();
      this.resize();
      util.setTimeout(this, function() {
        _this4.danmujs.start();
        if (_this4.player.paused) {
          _this4.danmujs.pause();
        }
        _this4.isOpen = true;
      }, 0);
    }
  }, {
    key: "stop",
    value: function stop() {
      this.isUseClose = true;
      if (!this.isOpen || !this.danmujs) {
        return;
      }
      this.danmujs.stop();
      this.config.isLive && this.clear();
      this.isOpen = false;
      this.hide();
    }
  }, {
    key: "clear",
    value: function clear() {
      this.danmujs && this.danmujs.clear();
    }
  }, {
    key: "setCommentLike",
    value: function setCommentLike(id, data) {
      this.danmujs && this.danmujs.setCommentLike(id, data);
    }
  }, {
    key: "setCommentDuration",
    value: function setCommentDuration(id, duration) {
      this.danmujs && this.danmujs.setCommentDuration(id, duration);
    }
  }, {
    key: "setAllDuration",
    value: function setAllDuration(mode, duration) {
      this.danmujs && this.danmujs.setAllDuration(mode, duration);
    }
  }, {
    key: "setCommentID",
    value: function setCommentID(oldID, newID) {
      this.danmujs && this.danmujs.setCommentID(oldID, newID);
    }
  }, {
    key: "hideMode",
    value: function hideMode(mode) {
      this.danmujs && this.danmujs.hide(mode);
    }
  }, {
    key: "showMode",
    value: function showMode(mode) {
      this.danmujs && this.danmujs.show(mode);
    }
  }, {
    key: "setArea",
    value: function setArea(area) {
      this.danmujs && this.danmujs.setArea(area);
    }
  }, {
    key: "setOpacity",
    value: function setOpacity(opacity) {
      this.danmujs && this.danmujs.setOpacity(opacity);
    }
  }, {
    key: "setFontSize",
    value: function setFontSize(size, channelSize) {
      this.danmujs && this.danmujs.setFontSize(size, channelSize);
    }
  }, {
    key: "resize",
    value: function resize() {
      this.danmujs && this.danmujs.resize();
    }
  }, {
    key: "sendComment",
    value: function sendComment(comments) {
      this.danmujs && this.danmujs.sendComment(comments);
    }
  }, {
    key: "updateComments",
    value: function updateComments(comments, isClear) {
      this.danmujs && this.danmujs.updateComments(comments, isClear);
    }
  }, {
    key: "hideIcon",
    value: function hideIcon() {
      this.danmuButton && this.danmuButton.hide();
    }
  }, {
    key: "showIcon",
    value: function showIcon() {
      this.danmuButton && this.danmuButton.show();
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.danmujs.stop();
      this.danmujs.destroy();
      this.danmujs = null;
      this.player.danmu = null;
      var danmuButton = this.danmuButton, danmuPanel = this.danmuPanel;
      this.danmuButton && this.danmuButton.root && danmuButton.__destroy && danmuButton.__destroy();
      this.danmuPanel && this.danmuPanel.root && danmuPanel.__destroy && danmuPanel.__destroy();
      this.danmuButton = null;
      this.danmuPanel = null;
    }
  }, {
    key: "render",
    value: function render() {
      return '<xg-danmu class="xgplayer-danmu">\n    </xg-danmu>';
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "danmu";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        comments: [],
        area: {
          start: 0,
          end: 1
        },
        closeDefaultBtn: false,
        defaultOff: false,
        panel: false,
        panelConfig: {},
        switchConfig: {},
        defaultOpen: true,
        isLive: false,
        channelSize: 24,
        fontSize: 14,
        opacity: 1,
        mouseControl: false,
        mouseControlPause: false,
        ext: {},
        style: {}
      };
    }
  }]);
  return Danmu2;
}(Plugin);
export { Danmu as default };
