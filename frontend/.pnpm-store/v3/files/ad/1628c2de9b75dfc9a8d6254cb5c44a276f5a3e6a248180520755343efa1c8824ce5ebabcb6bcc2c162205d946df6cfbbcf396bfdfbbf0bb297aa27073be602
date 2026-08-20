import { createClass as _createClass, classCallCheck as _classCallCheck, toConsumableArray as _toConsumableArray } from "../_virtual/_rollupPluginBabelHelpers.js";
import sniffer from "../utils/sniffer.js";
import XGLogger from "../plugins/logger/index.js";
import Replay from "../plugins/replay/index.js";
import Poster from "../plugins/poster/index.js";
import Start from "../plugins/start/index.js";
import Enter from "../plugins/enter/index.js";
import MiniScreen from "../plugins/miniScreen/index.js";
import PCPlugin from "../plugins/pc/index.js";
import MobilePlugin from "../plugins/mobile/index.js";
import Keyboard from "../plugins/keyboard/index.js";
import Loading from "../plugins/loading/index.js";
import Progress from "../plugins/progress/index.js";
import Play from "../plugins/play/index.js";
import Fullscreen from "../plugins/fullscreen/index.js";
import Time from "../plugins/time/index.js";
import TimeSegmentsControls from "../plugins/time/timesegments.js";
import Volume from "../plugins/volume/index.js";
import Rotate from "../plugins/rotate/index.js";
import PIP from "../plugins/pip/index.js";
import PlayNextIcon from "../plugins/playNext/index.js";
import Download from "../plugins/download/index.js";
import ScreenShot from "../plugins/screenShot/index.js";
import DefinitionIcon from "../plugins/definition/index.js";
import PlaybackRate from "../plugins/playbackRate/index.js";
import CssFullScreen from "../plugins/cssFullScreen/index.js";
import ErrorPlugin from "../plugins/error/index.js";
import Prompt from "../plugins/prompt/index.js";
import ProgressPreview from "../plugins/progressPreview/index.js";
import Thumbnail from "../plugins/common/thumbnail.js";
import MiniProgress from "../plugins/progress/miniProgress.js";
import DynamicBg from "../plugins/dynamicBg/index.js";
import ZH from "../lang/zh-cn.js";
import Stats from "../plugins/stats/index.js";
import GapJump from "../plugins/gapJump/index.js";
import WaitingTimeoutJump from "../plugins/waitingTimeoutJump/index.js";
import TestSpeed from "../plugins/testspeed/index.js";
import I18N from "../lang/i18n.js";
import FpsDetect from "../plugins/fpsDetect/index.js";
I18N.use(ZH);
var DefaultPreset = /* @__PURE__ */ _createClass(function DefaultPreset2(options, playerConfig) {
  var _this$plugins, _this$plugins2, _this$plugins3;
  _classCallCheck(this, DefaultPreset2);
  var simulateMode = playerConfig && playerConfig.isMobileSimulateMode === "mobile";
  var isLive = playerConfig.isLive;
  var vodPlugins = isLive ? [] : [TimeSegmentsControls, Progress, MiniProgress, ProgressPreview, Time];
  var contolsIcons = [].concat(vodPlugins, [Play, Fullscreen, Rotate, PlayNextIcon, DefinitionIcon, PlaybackRate, Download, ScreenShot, Volume, PIP]);
  var layers = [Replay, Poster, Start, Loading, Enter, ErrorPlugin, Prompt, Thumbnail, MiniScreen];
  this.plugins = [Stats, XGLogger].concat(_toConsumableArray(contolsIcons), layers, [GapJump, WaitingTimeoutJump]);
  var mode = simulateMode ? "mobile" : sniffer.device;
  switch (mode) {
    case "pc":
      (_this$plugins = this.plugins).push.apply(_this$plugins, [Keyboard, PCPlugin, CssFullScreen, TestSpeed, FpsDetect]);
      break;
    case "mobile":
      (_this$plugins2 = this.plugins).push.apply(_this$plugins2, [MobilePlugin]);
      break;
    default:
      (_this$plugins3 = this.plugins).push.apply(_this$plugins3, [Keyboard, PCPlugin, CssFullScreen]);
  }
  if (sniffer.os.isIpad || mode === "pc") {
    this.plugins.push(DynamicBg);
  }
  if (sniffer.os.isIpad) {
    this.plugins.push(PCPlugin);
  }
  this.ignores = [];
  this.i18n = [];
});
export { DefaultPreset as default };
