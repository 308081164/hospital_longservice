import { inherits as _inherits, createSuper as _createSuper, classCallCheck as _classCallCheck, createClass as _createClass, objectSpread2 as _objectSpread2, get as _get, getPrototypeOf as _getPrototypeOf } from "../../_virtual/_rollupPluginBabelHelpers.js";
import util from "../../utils/util.js";
import { DEFINITION_CHANGE } from "../../events.js";
import "../../utils/debug.js";
import { POSITIONS } from "../../plugin/plugin.js";
import OptionsIcon from "../common/optionsIcon.js";
var DefinitionIcon = /* @__PURE__ */ function(_OptionsIcon) {
  _inherits(DefinitionIcon2, _OptionsIcon);
  var _super = _createSuper(DefinitionIcon2);
  function DefinitionIcon2(args) {
    var _this;
    _classCallCheck(this, DefinitionIcon2);
    _this = _super.call(this, args);
    _this.curTime = 0;
    _this.isPaused = true;
    return _this;
  }
  _createClass(DefinitionIcon2, [{
    key: "beforeCreate",
    value: function beforeCreate(args) {
      var list = args.config.list;
      if (Array.isArray(list) && list.length > 0) {
        args.config.list = list.map(function(item) {
          if (!item.text && item.name) {
            item.text = item.name;
          }
          if (!item.text) {
            item.text = item.definition;
          }
          return item;
        });
      }
    }
  }, {
    key: "afterCreate",
    value: function afterCreate() {
      var _this2 = this;
      _get(_getPrototypeOf(DefinitionIcon2.prototype), "afterCreate", this).call(this);
      this.on("resourceReady", function(list) {
        _this2.changeDefinitionList(list);
      });
      this.on(DEFINITION_CHANGE, function(data) {
        _this2.renderItemList(_this2.config.list, data.to);
      });
      if (this.player.definitionList.length < 2) {
        this.hide();
      }
    }
  }, {
    key: "show",
    value: function show(value) {
      if (!this.config.list || this.config.list.length < 2) {
        return;
      }
      util.addClass(this.root, "show");
    }
  }, {
    key: "initDefinition",
    value: function initDefinition() {
      var _this$config = this.config, list = _this$config.list, defaultDefinition = _this$config.defaultDefinition;
      if (list.length > 0) {
        var to = null;
        list.map(function(item) {
          if (item.definition === defaultDefinition) {
            to = item;
          }
        });
        if (!to) {
          to = list[0];
        }
        this.changeDefinition(to);
      }
    }
  }, {
    key: "renderItemList",
    value: function renderItemList() {
      var _this3 = this;
      var list = arguments.length > 0 && arguments[0] !== void 0 ? arguments[0] : this.config.list || [];
      var to = arguments.length > 1 ? arguments[1] : void 0;
      var targetDef = to && to.definition ? to.definition : this.config.defaultDefinition;
      if (to) {
        list.forEach(function(item) {
          item.selected = false;
        });
      }
      var curIndex = 0;
      var items = list.map(function(item, index) {
        var showItem = _objectSpread2(_objectSpread2({}, item), {}, {
          showText: _this3.getTextByLang(item) || item.definition,
          selected: false
        });
        if (item.selected || item.definition && item.definition == targetDef) {
          showItem.selected = true;
          curIndex = index;
        }
        return showItem;
      });
      _get(_getPrototypeOf(DefinitionIcon2.prototype), "renderItemList", this).call(this, items, curIndex);
    }
  }, {
    key: "changeDefinitionList",
    value: function changeDefinitionList(list) {
      if (!Array.isArray(list)) {
        return;
      }
      this.config.list = list.map(function(item) {
        if (!item.text && item.name) {
          item.text = item.name;
        }
        if (!item.text) {
          item.text = item.definition;
        }
        return item;
      });
      this.renderItemList();
      this.config.list.length < 2 ? this.hide() : this.show();
    }
  }, {
    key: "changeDefinition",
    value: function changeDefinition(to, from) {
      this.player.changeDefinition(to, from);
    }
  }, {
    key: "onItemClick",
    value: function onItemClick(e, data) {
      var definitionList = this.player.definitionList;
      _get(_getPrototypeOf(DefinitionIcon2.prototype), "onItemClick", this).apply(this, arguments);
      this.emitUserAction(e, "change_definition", {
        from: data.from,
        to: data.to
      });
      for (var i = 0; i < definitionList.length; i++) {
        if (data.to && definitionList[i].definition === data.to.definition) {
          data.to.url = definitionList[i].url;
        }
        if (data.from && definitionList[i].definition === data.from.definition) {
          data.from.url = definitionList[i].url;
        }
      }
      this.player.changeDefinition(data.to, data.from);
    }
  }], [{
    key: "pluginName",
    get: function get() {
      return "definition";
    }
  }, {
    key: "defaultConfig",
    get: function get() {
      return _objectSpread2(_objectSpread2({}, OptionsIcon.defaultConfig), {}, {
        position: POSITIONS.CONTROLS_RIGHT,
        index: 3,
        list: [],
        defaultDefinition: "",
        disable: false,
        hidePortrait: false,
        className: "xgplayer-definition",
        isShowIcon: true
      });
    }
  }]);
  return DefinitionIcon2;
}(OptionsIcon);
export { DefinitionIcon as default };
