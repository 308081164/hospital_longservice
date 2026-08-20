type NodeFactory<T> = (node?: HTMLElement) => T;
type OnNodeDestroy = (node: HTMLElement) => void;
export declare class RecyclableNodeFactory {
    private readonly elementFactory;
    private readonly idleNodes;
    private readonly activeNodes;
    constructor(elementFactory: NodeFactory<HTMLElement>, initialNodes?: number);
    /**
     * 获取一个可用的节点，如果没有可用的节点则创建一个新节点
     */
    getNode(): HTMLElement;
    /**
     * 回收一个节点，将其放入 idleNodes 中等待重用
     * @param node - 要回收的节点
     */
    recycle(node: HTMLElement): void;
    /**
     * 完全清理所有缓存节点
     * @param onNodeDestroy
     */
    destroy(onNodeDestroy?: OnNodeDestroy): void;
}
export {};
