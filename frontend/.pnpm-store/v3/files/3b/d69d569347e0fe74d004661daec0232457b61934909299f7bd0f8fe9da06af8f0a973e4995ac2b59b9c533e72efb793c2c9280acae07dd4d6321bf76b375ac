import { createClass as _createClass, classCallCheck as _classCallCheck } from "../_virtual/_rollupPluginBabelHelpers.js";
import sniffer from "../utils/sniffer.js";
import Poster from "../plugins/poster/index.js";
import Start from "../plugins/start/index.js";
import Enter from "../plugins/enter/index.js";
import PCPlugin from "../plugins/pc/index.js";
import MobilePlugin from "../plugins/mobile/index.js";
import Keyboard from "../plugins/keyboard/index.js";
import Loading from "../plugins/loading/index.js";
import Play from "../plugins/play/index.js";
import Fullscreen from "../plugins/fullscreen/index.js";
import Time from "../plugins/time/index.js";
import Volume from "../plugins/volume/index.js";
import Rotate from "../plugins/rotate/index.js";
import PIP from "../plugins/pip/index.js";
import CssFullScreen from "../plugins/cssFullScreen/index.js";
import DefinitionIcon from "../plugins/definition/index.js";
import PlaybackRate from "../plugins/playbackRate/index.js";
import ErrorPlugin from "../plugins/error/index.js";
import ZH from "../lang/zh-cn.js";
var DefaultPreset = /* @__PURE__ */ _createClass(function DefaultPreset2() {
  var _this$plugins, _this$plugins2;
  _classCallCheck(this, DefaultPreset2);
  var contolsIcons = [Play, Fullscreen, Time, Volume, Rotate, DefinitionIcon, PlaybackRate, CssFullScreen, PIP];
  this.plugins = [Poster, Start, Loading, Enter, ErrorPlugin].concat(contolsIcons);
  switch (sniffer.device) {
    case "pc":
      (_this$plugins = this.plugins).push.apply(_this$plugins, [Keyboard, PCPlugin]);
      break;
    case "mobile":
      this.plugins.push(MobilePlugin);
      break;
    default:
      (_this$plugins2 = this.plugins).push.apply(_this$plugins2, [Keyboard, PCPlugin]);
  }
  this.ignores = [];
  this.i18n = [ZH];
});
export { DefaultPreset as default };
