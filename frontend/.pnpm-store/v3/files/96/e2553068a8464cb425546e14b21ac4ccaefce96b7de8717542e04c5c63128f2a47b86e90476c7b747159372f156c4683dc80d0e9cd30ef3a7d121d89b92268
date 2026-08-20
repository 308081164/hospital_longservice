export default DanmuPanel;
export type IDanmuPanelConfig = {
    position?: string;
    index?: number;
    onChangeSet?: () => any;
    speed?: number;
    area?: any;
    opacity?: number;
    fonSize?: number;
};
/**
  * @typedef {{
  *   position?: string,
  *   index?: number,
  *   onChangeSet?: () => any,
  *   speed?: number,
  *   area?: any,
  *   opacity?: number,
  *   fonSize?: number
  * }} IDanmuPanelConfig
  */
declare class DanmuPanel extends Plugin {
    /**
     * @type IDanmuPanelConfig
     */
    static get defaultConfig(): IDanmuPanelConfig;
    constructor(args: any);
    set: {
        speed: number;
        area: {};
        opacity: number;
        fonSize: string;
    };
    activeEvent: string;
    onStateChange(e: any): void;
    onToggle(e: any): void;
    render(): string;
}
import Plugin from "../../plugin";
