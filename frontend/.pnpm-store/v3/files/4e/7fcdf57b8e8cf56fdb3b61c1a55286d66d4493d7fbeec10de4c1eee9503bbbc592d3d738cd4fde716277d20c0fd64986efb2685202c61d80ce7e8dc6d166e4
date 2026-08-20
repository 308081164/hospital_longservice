export default Prompt;
/**
 * 消息组件
 */
declare class Prompt extends Plugin {
    static get defaultConfig(): {
        interval: number;
        style: {};
        mode: string;
        autoHide: boolean;
        detail: {
            text: string;
            highlight: string;
        };
        onClick: () => void;
    };
    /**
     * @type {number}
     */
    intervalId: number;
    customConfig: any;
    setStyle(style: any): void;
    showPrompt(detail: any, config?: {}, onClick?: () => void): void;
    customOnClick: () => void;
    render(): string;
}
import Plugin from "../../plugin";
