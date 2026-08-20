import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, defineProperty as _defineProperty, assertThisInitialized as _assertThisInitialized } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { READY, AUTOPLAY_STARTED, AUTOPLAY_PREVENTED, PLAY, PAUSE, RESET } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
import { STATES } from "../../state.js";
import PlayIcon from "../assets/play.js";
import PauseIcon from "../assets/pause.js";
var AnimateMap = {};
function addAnimate(key, seconds) {
  var callback = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {
    start: null,
    end: null
  };
  if (AnimateMap[key]) {
    window.clearTimeout(AnimateMap[key].id);
  }
  AnimateMap[key] = {};
  callback.start && callback.start();
  AnimateMap[key].id = window.setTimeout(function() {
    callback.end && callback.end();
    window.clearTimeout(AnimateMap[key].id);
    delete AnimateMap[key];
  }, seconds);
  return AnimateMap[key].id;
}
function clearAnimation(id) {
  if (id) {
    window.clearTimeout(id);
    return;
  }
  Object.keys(AnimateMap).map(function(key) {
    window.clearTimeout(AnimateMap[key].id);
    delete AnimateMap[key];
  });
}
var Start = /* @__PURE__ */ function(_Plugin) {
  _inherits(Start2, _Plugin);
  var _super = _createSuper(Start2);
  function Start2(args) {
    var _this;
    _classCallCheck(this, Start2);
    _this = _super.call(this, args);
    _defineProperty(_assertThisInitialized(_this), "onPlayerReset", function() {
      _this.autoPlayStart = false;
      var className = _this.config.mode === "auto" ? "auto-hide" : "hide";
      _this.setAttr("data-state", "play");
      util.removeClass(_this.root, className);
      _this.show();
    });
    _defineProperty(_assertThisInitialized(_this), "onAutoplayStart", function() {
      if (_this.autoPlayStart) {
        return;
      }
      var className = _this.config.mode === "auto" ? "auto-hide" : "hide";
      util.addClass(_this.root, className);
      _this.autoPlayStart = true;
      _this.toggleTo("play");
    });
    _this.autoPlayStart = false;
    return _this;
  }
  _createClass(Start2, [{
    key: "afterCreate",
    value: function afterCreate() {
      var playerConfig = this.playerConfig;
      this.initIcons();
      this.listenEvents();
      this.bindClickEvents();
      if (!playerConfig.autoplay) {
        this.show();
      }
    }
  }, {
    key: "listenEvents",
    value: function listenEvents() {
      var _this2 = this;
      var player = this.player, playerConfig = this.playerConfig;
      this.once(READY, function() {
        if (playerConfig) {
          if (playerConfig.lang && playerConfig.lang === "en") {
            util.addClass(player.root, "lang-is-en");
          } else if (playerConfig.lang === "jp") {
            util.addClass(player.root, "lang-is-jp");
          }
        }
      });
      this.on(AUTOPLAY_STARTED, this.onAutoplayStart);
      this.on(AUTOPLAY_PREVENTED, function() {
        var className = _this2.config.mode === "auto" ? "auto-hide" : "hide";
        _this2.setAttr("data-state", "play");
        util.removeClass(_this2.root, className);
        _this2.show();
      });
      this.on(PLAY, function() {
        _this2.toggleTo("play");
      });
      this.on(PAUSE, function() {
        _this2.toggleTo("pause");
      });
      this.on(RESET, function() {
        _this2.onPlayerReset();
      });
    }
  }, {
    key: "bindClickEvents",
    value: function bindClickEvents() {
      var _this3 = this;
      this.clickHandler = this.hook("startClick", this.switchPausePlay, {
        pre: function pre(e) {
          e.cancelable && e.preventDefault();
          e.stopPropagation();
          var paused = _this3.player.paused;
          _this3.emitUserAction(e, "switch_play_pause", {
            props: "paused",
            from: paused,
            to: !paused
          });
        }
      });
      this.bind(["click", "touchend"], this.clickHandler);
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        startPlay: {
          icon: PlayIcon,
          class: "xg-icon-play"
        },
        startPause: {
          icon: PauseIcon,
          class: "xg-icon-pause"
        }
      };
    }
  }, {
    key: "initIcons",
    value: function initIcons() {
      var icons = this.icons;
      this.appendChild("xg-start-inner", icons.startPlay);
      this.appendChild("xg-start-inner", icons.startPause);
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
    key: "focusHide",
    value: function focusHide() {
      util.addClass(this.root, "focus-hide");
    }
  }, {
    key: "recover",
    value: function recover() {
      util.removeClass(this.root, "focus-hide");
    }
  }, {
    key: "switchStatus",
    value: function switchStatus(isAnimate) {
      if (isAnimate) {
        this.setAttr("data-state", !this.player.paused ? "play" : "pause");
      } else {
        this.setAttr("data-state", this.player.paused ? "play" : "pause");
      }
    }
  }, {
    key: "animate",
    value: function animate(endShow) {
      var _this4 = this;
      this._animateId = addAnimate("pauseplay", 400, {
        start: function start() {
          util.addClass(_this4.root, "interact");
          _this4.show();
          _this4.switchStatus(true);
        },
        end: function end() {
          util.removeClass(_this4.root, "interact");
          !endShow && _this4.hide();
          _this4._animateId = null;
        }
      });
    }
  }, {
    key: "endAnimate",
    value: function endAnimate() {
      util.removeClass(this.root, "interact");
      clearAnimation(this._animateId);
      this._animateId = null;
    }
  }, {
    key: "switchPausePlay",
    value: function switchPausePlay(e) {
      var player = this.player;
      e.cancelable && e.preventDefault();
      e.stopPropagation();
      if (player.state < STATES.READY) {
        return;
      }
      var paused = this.player.paused;
      if (!paused && player.state === STATES.RUNNING) {
        player.pause();
      } else {
        player.play();
      }
    }
  }, {
    key: "onPlayPause",
    value: function onPlayPause(status) {
      this.toggleTo(status);
    }
  }, {
    key: "toggleTo",
    value: function toggleTo(status) {
      var config = this.config, player = this.player;
      if (!player || player.state < STATES.RUNNING || !this.autoPlayStart) {
        return;
      }
      if (config.mode === "show") {
        this.switchStatus();
        this.show();
        return;
      }
      if (config.mode === "auto") {
        this.switchStatus();
        return;
      }
      if (config.isShowPause && player.paused && !player.ended || config.isShowEnd && player.ended) {
        this.switchStatus();
        this.show();
        this.endAnimate();
        return;
      }
      if (config.disableAnimate) {
        this.switchStatus();
        this.hide();
        return;
      }
      if (status === "play") {
        this.autoPlayStart ? this.animate() : this.hide();
      } else {
        if (!this.autoPlayStart || player.ended) {
          return;
        }
        this.animate();
      }
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.unbind(["click", "touchend"], this.clickHandler);
      clearAnimation(this._animateId);
    }
  }, {
    key: "render",
    value: function render() {
      var className = this.playerConfig.autoplay ? this.config.mode === "auto" ? "auto-hide" : "hide" : "";
      return '\n    <xg-start class="xgplayer-start '.concat(className, '">\n    <xg-start-inner></xg-start-inner>\n    </xg-start>');
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "start";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        isShowPause: false,
        isShowEnd: false,
        disableAnimate: false,
        mode: "hide"
      };
    }
  }]);
  return Start2;
}(Plugin);
export { Start as default };
