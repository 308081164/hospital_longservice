export default MiniProgress;
declare class MiniProgress extends Plugin {
    static get defaultConfig(): {
        mode: string;
        height: number;
    };
    get offsetDuration(): number;
    get currentTime(): any;
    onTimeupdate: () => void;
    reset(): void;
    update(data: {
        cached: number;
        played: number;
    }, duration: any): void;
    render(): string;
}
import Plugin from "../../plugin";
