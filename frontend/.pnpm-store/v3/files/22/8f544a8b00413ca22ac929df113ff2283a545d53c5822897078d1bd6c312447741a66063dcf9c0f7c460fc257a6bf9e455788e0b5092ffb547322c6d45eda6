import { inherits as _inherits, createSuper as _createSuper, createClass as _createClass, classCallCheck as _classCallCheck, objectSpread2 as _objectSpread2 } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { VIDEO_RESIZE, DURATION_CHANGE } from "../../events.js";
import "../../utils/debug.js";
import Plugin from "../../plugin/plugin.js";
var HeatMap = /* @__PURE__ */ function(_Plugin) {
  _inherits(HeatMap2, _Plugin);
  var _super = _createSuper(HeatMap2);
  function HeatMap2(args) {
    var _this;
    _classCallCheck(this, HeatMap2);
    _this = _super.call(this, args);
    _this.canvasHeight = 0;
    _this.canvasWidth = 0;
    _this.width = 0;
    _this.height = 0;
    _this.dataFloatLen = 2;
    _this.xData = [];
    _this.yData = [];
    _this.yMax = 0;
    _this._duration = 0;
    _this.curData = [];
    return _this;
  }
  _createClass(HeatMap2, [{
    key: "afterPlayerInit",
    value: function afterPlayerInit() {
      if (this.root) {
        return;
      }
      this.createRoot();
      this.reset();
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      this.on(VIDEO_RESIZE, function() {
        _this2.resize();
      });
      this.on(DURATION_CHANGE, function() {
        _this2._duration = _this2.player.duration;
        _this2.reset();
      });
    }
  }, {
    key: "setConfig",
    value: function setConfig(config) {
      var _this3 = this;
      Object.keys(config).forEach(function(key) {
        _this3.config[key] = config[key];
      });
      this.reset();
    }
  }, {
    key: "reset",
    value: function reset() {
      var data = this.config.data;
      this.setData(data);
      this.resize();
    }
  }, {
    key: "createRoot",
    value: function createRoot() {
      if (this.root) {
        return;
      }
      var mode = this.config.mode;
      var progress = this.player.plugins.progress;
      var parent = progress.root;
      var _class = mode === "activeShow" ? "xg-heatmap-active-show" : "xg-heatmap-normal";
      var root = util.createDom("div", "", {}, "xg-heatmap ".concat(_class));
      var c = parent.children.length > 0 ? parent.children[0] : null;
      parent.insertBefore(root, c);
      this.root = root;
      var canvas = util.createDom("canvas", "", {}, "xg-heatmap-canvas");
      this.root.appendChild(canvas);
      this.root.style.height = "".concat(this.config.height, "px");
      this.canvas = canvas;
    }
  }, {
    key: "resize",
    value: function resize() {
      var dpi = this.config.dpi;
      var _this$root$getBoundin = this.root.getBoundingClientRect(), width = _this$root$getBoundin.width, height = _this$root$getBoundin.height;
      if (width === this.width && height * dpi === this.height) {
        return;
      }
      this.width = width;
      this.height = height;
      this.canvas.width = this.canvasWidth = width * dpi;
      this.canvas.height = this.canvasHeight = height * dpi;
      this.yMax = 0;
      if (this.curData.length) {
        this.setData();
        this.drawLinePath();
      }
    }
  }, {
    key: "formatData",
    value: function formatData(data, duration, minValue) {
      var nData = [];
      if (data.length < 1) {
        return nData;
      }
      var totalDur = parseInt(duration * 1e3, 10);
      if (util.typeOf(data[0]) === "Object" && data[0].time !== void 0) {
        if (!totalDur) {
          return nData;
        }
        var step = 1;
        var lastTime = 0;
        data.forEach(function(item) {
          var dur = parseInt(item.time * 1e3, 10);
          if (lastTime && (step < 0 || item.time - lastTime < step)) {
            step = item.time - lastTime;
          }
          lastTime = item.time;
          nData.push(_objectSpread2(_objectSpread2({}, item), {}, {
            percent: dur / totalDur
          }));
        });
        var fTime = nData[0].time;
        if (fTime > 0) {
          var arr = fTime - step > 0 ? [fTime - step, 0] : [0];
          arr.forEach(function(time) {
            var dur = parseInt(time * 1e3, 10);
            nData.unshift({
              time,
              score: minValue,
              percent: dur / totalDur
            });
          });
        }
        var last = nData[nData.length - 1];
        if (duration - last.time > step) {
          [last.time + step, duration].forEach(function(item) {
            var dur = parseInt(item * 1e3, 10);
            nData.push({
              time: item,
              score: minValue,
              percent: dur / totalDur
            });
          });
        }
      } else {
        data.forEach(function(item) {
          nData.push(item);
        });
      }
      return nData;
    }
  }, {
    key: "_getX",
    value: function _getX(index, stepX, item, width) {
      if (item.percent !== void 0) {
        return this.fixFloat(width * item.percent, this.dataFloatLen);
      } else {
        return this.fixFloat((index - 1) * stepX, this.dataFloatLen);
      }
    }
  }, {
    key: "_getY",
    value: function _getY(item, stepY, maxY) {
      var _this$config = this.config, maxValue = _this$config.maxValue, minValue = _this$config.minValue;
      var y = minValue;
      y = item.score !== void 0 ? item.score : item;
      y = Math.min(y, maxValue);
      y = Math.max(y, minValue);
      y = y / stepY;
      y = 1 - y;
      y = this.fixFloat(y * maxY, this.dataFloatLen);
      return y;
    }
  }, {
    key: "setData",
    value: function setData(data) {
      var _this$config2 = this.config, maxValue = _this$config2.maxValue, minValue = _this$config2.minValue, maxLength = _this$config2.maxLength;
      if (data && data.length) {
        this.curData = this.formatData(data, this._duration, minValue);
      }
      data = this.curData;
      if (data.length < 1) {
        return;
      }
      this.xData = [];
      this.yData = [];
      this.yMax = 0;
      var max = maxValue;
      var min = minValue;
      var step_V = max - min;
      var step_Y = this.canvasHeight;
      var curDataLength = data.length;
      var step_D = curDataLength > maxLength ? this.fixFloat(curDataLength / maxLength, this.dataFloatLen) : 1;
      curDataLength = parseInt(curDataLength / step_D);
      var step_X = this.canvasWidth / (curDataLength - 1);
      step_X = this.fixFloat(step_X, this.dataFloatLen);
      if (Number.isNaN(step_X)) {
        return;
      }
      var i = parseInt(this.fixFloat(step_D - 1, 0));
      var j = 0;
      while (j < curDataLength) {
        var x = this._getX(j, step_X, data[i], this.canvasWidth);
        this.xData.push(x);
        var y = this._getY(data[i], step_V, step_Y);
        this.yMax = Math.max(this.yMax, step_Y - y);
        this.yData.push(y);
        j++;
        i = parseInt(this.fixFloat(j * step_D, 0));
      }
    }
  }, {
    key: "_getFillStyle",
    value: function _getFillStyle(ctx) {
      var _this$config3 = this.config, gradient = _this$config3.gradient, gradientColors = _this$config3.gradientColors, fillStyle = _this$config3.fillStyle;
      var fStyle = fillStyle;
      if (gradient && gradient.length === 4) {
        var gradientStyle = ctx.createLinearGradient(gradient[0], gradient[1], gradient[2], gradient[3]);
        if (gradientColors.length < 2) {
          console.warn(this.pluginName, "\u6E10\u53D8\u989C\u8272\u914D\u7F6EgradientColors\u5F02\u5E38");
        } else {
          gradientColors.forEach(function(item) {
            gradientStyle.addColorStop(item.start, item.color);
          });
          fStyle = gradientStyle;
        }
      }
      return fStyle;
    }
  }, {
    key: "drawLinePath",
    value: function drawLinePath() {
      this.clearCanvas();
      var ctx = this.canvas.getContext("2d");
      var xData = this.xData, yData = this.yData;
      var x, y;
      var i = 0;
      var _this$config4 = this.config, lineWidth = _this$config4.lineWidth, alpha = _this$config4.alpha, strokeStyle = _this$config4.strokeStyle;
      var fillStyle = this._getFillStyle(ctx);
      ctx.beginPath();
      ctx.lineWidth = lineWidth;
      ctx.globalAlpha = alpha;
      ctx.strokeStyle = strokeStyle || fillStyle;
      ctx.fillStyle = fillStyle;
      ctx.moveTo(0, this.canvasHeight);
      x = xData[i];
      y = yData[i];
      while (x !== void 0 && y !== void 0) {
        ctx.lineTo(x, y);
        i++;
        x = xData[i];
        y = yData[i];
      }
      ctx.lineTo(this.canvasWidth, this.canvasHeight);
      ctx.closePath();
      ctx.stroke();
      ctx.fill();
    }
  }, {
    key: "clearCanvas",
    value: function clearCanvas() {
      this.canvas.width = this.canvasWidth;
      this.canvas.height = this.canvasHeight;
    }
  }, {
    key: "fixFloat",
    value: function fixFloat(_num, _length) {
      if (typeof _num === "number") {
        return parseFloat(_num.toFixed(_length));
      }
      return NaN;
    }
  }, {
    key: "render",
    value: function render() {
      return "";
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "heatmap";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return {
        lineWidth: 0,
        dpi: 2,
        alpha: 0.7,
        gradient: [],
        gradientColors: [],
        strokeStyle: "",
        fillColor: "#FA1F41",
        height: 20,
        data: [],
        maxValue: 100,
        minValue: 0,
        maxLength: 400,
        mode: "activeShow"
      };
    }
  }]);
  return HeatMap2;
}(Plugin);
export { HeatMap as default };
