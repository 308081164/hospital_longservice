import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck } from "../../_virtual/_rollupPluginBabelHelpers.js";
import "../../utils/util.js";
import "../../utils/debug.js";
import Plugin, { POSITIONS } from "../../plugin/plugin.js";
import { xgIconTips } from "../common/iconTools.js";
var DANMU_OPEN = '<dk-switch class="danmu-switch">\n<span class="txt">\u5F39</span>\n</dk-switch>';
var DanmuIcon = /* @__PURE__ */ function(_Plugin) {
  _inherits(DanmuIcon2, _Plugin);
  var _super = _createSuper(DanmuIcon2);
  function DanmuIcon2() {
    _classCallCheck(this, DanmuIcon2);
    return _super.apply(this, arguments);
  }
  _createClass(DanmuIcon2, [{
    key: "afterCreate",
    value: function afterCreate() {
      this.initIcons();
      this.onStateChange = this.onStateChange.bind(this);
      this.bind(["click", "touchend"], this.onStateChange);
    }
  }, {
    key: "registerIcons",
    value: function registerIcons() {
      return {
        openDanmu: {
          icon: DANMU_OPEN,
          class: "danmu-switch-open"
        },
        closeDanmu: {
          icon: DANMU_OPEN,
          class: "danmu-switch-closed"
        }
      };
    }
  }, {
    key: "switchState",
    value: function switchState(isOpen) {
      if (isOpen) {
        this.setAttr("data-state", "active");
      } else {
        this.setAttr("data-state", "normal");
      }
      this.switchTips(isOpen);
    }
  }, {
    key: "initIcons",
    value: function initIcons() {
      var icons = this.icons;
      var contentIcon = this.find(".xgplayer-icon");
      contentIcon.appendChild(icons.openDanmu);
      contentIcon.appendChild(icons.closeDanmu);
    }
  }, {
    key: "switchTips",
    value: function switchTips(isOpen) {
      var tipDom = this.find(".xg-tips");
      tipDom && this.changeLangTextKey(tipDom, isOpen ? "OFF" : "OPEN");
    }
  }, {
    key: "onStateChange",
    value: function onStateChange(e) {
      e.preventDefault();
      e.stopPropagation();
      var state = this.root.getAttribute("data-state");
      var isOpen = state === "active";
      this.switchState(!isOpen);
      this.config.onSwitch && this.config.onSwitch(e, !isOpen);
    }
  }, {
    key: "destroy",
    value: function destroy() {
      this.unbind(["click", "touchend"], this.getMini);
    }
  }, {
    key: "render",
    value: function render() {
      var langKey = "OPEN";
      return '\n    <xg-icon class="danmu-icon">\n      <div class="xgplayer-icon">\n      </div>\n      '.concat(xgIconTips(this, langKey, this.playerConfig.isHideTips), "\n    </xg-icon>");
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "danmuIcon";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 11,
        onSwitch: function onSwitch(event, state) {
        }
      };
    }
  }]);
  return DanmuIcon2;
}(Plugin);
export { DanmuIcon as default };
