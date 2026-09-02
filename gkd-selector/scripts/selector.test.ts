import assert from 'node:assert/strict';

import {
  CompareOperator,
  FastQuery,
  MatchOptions,
  Selector,
  SelectorCompileResult,
  SelectorException,
  SelectorParseResult,
  SelectorMatch,
  JsSelectorTypeKind,
  SelectorSyntaxException,
  JsSelectorTypeModelBuilder,
  SelectorTypeResult,
  JsNodeAdapter,
  createDefaultSelectorTypeModel,
} from '@gkd-kit/selector';

if (false) {
  // Protect the exported TypeScript contract: only searchable operators construct text fast queries.
  new FastQuery.Text('value', CompareOperator.Equal);
  // @ts-expect-error NotEqual cannot produce a complete text fast-query candidate set.
  new FastQuery.Text('value', CompareOperator.NotEqual);
}

type TreeNode = {
  name: string;
  id?: string;
  text?: string;
  parent?: TreeNode;
  children: TreeNode[];
};

if (false) {
  // Protect the JS node contract: null is reserved for absence and matching failure.
  // @ts-expect-error Node adapter types must exclude null.
  abstract class NullableNodeAdapter extends JsNodeAdapter<TreeNode | null> {}
  // @ts-expect-error Successful match results must contain non-null nodes.
  type NullableSelectorMatch = SelectorMatch<TreeNode | null>;

  type NodeKey = ReturnType<JsNodeAdapter<TreeNode>['getNodeKey']>;
  const validNodeKey: NodeKey = 'node-id';
  // @ts-expect-error A node key must exclude null and undefined.
  const missingNodeKey: NodeKey = undefined;
  void validNodeKey;
  void missingNodeKey;
}

class TreeNodeAdapter extends JsNodeAdapter<TreeNode> {
  invokeArgsWereArray = false;

  getAttr(target: unknown, name: string): unknown {
    return (target as Record<string, unknown>)[name];
  }

  getName(node: TreeNode): string {
    return node.name;
  }

  getChildCount(node: TreeNode): number {
    return node.children.length;
  }

  getChild(node: TreeNode, index: number): TreeNode | undefined {
    return node.children[index];
  }

  getParent(node: TreeNode): TreeNode | undefined {
    return node.parent;
  }

  getNodeKey(node: TreeNode): TreeNode {
    return node;
  }

  override getInvoke(
    target: unknown,
    name: string,
    args: unknown[],
  ): unknown {
    this.invokeArgsWereArray = Array.isArray(args);
    return name === 'echo' ? args[0] : null;
  }
}

class TrackingFastQueryNodeAdapter extends TreeNodeAdapter {
  fastQueryArgsWereArray = false;
  yieldedCount = 0;

  override *getFastQueryDescendants(
    node: TreeNode,
    fastQueryList: FastQuery[],
  ): Generator<TreeNode> {
    this.fastQueryArgsWereArray = Array.isArray(fastQueryList);
    for (const child of node.children) {
      this.yieldedCount += 1;
      yield child;
    }
  }
}

class MissingKeyNodeAdapter extends TreeNodeAdapter {
  override getNodeKey(node: TreeNode): TreeNode {
    return node.id as unknown as TreeNode;
  }
}

const source = "Button[id='confirm']";
const tokens = Selector.tokenize(source);
assert.ok(Array.isArray(tokens));
assert.equal(tokens.at(0)?.start, 0);
assert.equal(tokens.at(-1)?.end, source.length);

const compileResult = Selector.compile(source);
assert.ok(compileResult instanceof SelectorCompileResult.Success);
const selector = compileResult.value;

const parseResult = Selector.parse(source);
assert.ok(parseResult instanceof SelectorParseResult.Success);
assert.ok(Array.isArray(parseResult.tokens));
assert.ok(Array.isArray(parseResult.positions));

const invalidSource = 'Button[id=]';
const invalidResult = Selector.compile(invalidSource);
assert.ok(invalidResult instanceof SelectorCompileResult.Failure);
const syntaxError = invalidResult.error;
assert.ok(syntaxError instanceof Error);
assert.ok(syntaxError instanceof SelectorException);
assert.ok(syntaxError instanceof SelectorSyntaxException);
assert.equal(syntaxError.expected, 'value');
assert.equal(syntaxError.actual, ']');
assert.equal(syntaxError.index, invalidSource.indexOf(']'));
assert.equal(syntaxError.range.start, syntaxError.index);
assert.match(syntaxError.message, /Expected value/);
assert.throws(
  () => invalidResult.value,
  (error: unknown) => error === syntaxError,
);

const regexSource = "Button[text~='(']";
const regexResult = Selector.compile(regexSource);
assert.ok(regexResult instanceof SelectorCompileResult.Failure);
assert.equal(regexResult.error.range.start, regexSource.indexOf("'"));
assert.equal(regexResult.error.range.end, regexSource.lastIndexOf("'") + 1);
assert.ok(regexResult.error.detail);
assert.match(regexResult.error.message, /at index/);

const confirm: TreeNode = {
  name: 'Button',
  id: 'confirm',
  text: 'Confirm',
  children: [],
};
const cancel: TreeNode = {
  name: 'Button',
  id: 'cancel',
  children: [],
};
const root: TreeNode = {
  name: 'Root',
  children: [confirm, cancel],
};
confirm.parent = root;
cancel.parent = root;

const adapter = new TreeNodeAdapter();
assert.equal(adapter.match(confirm, selector), confirm);
assert.equal(
  adapter.match(
    confirm,
    Selector.compile("Button[text.substring(0,2)='Co']").value,
  ),
  confirm,
);
assert.equal(
  adapter.match(confirm, Selector.compile("Button[echo(text)='Confirm']").value),
  confirm,
);
assert.equal(adapter.invokeArgsWereArray, true);
assert.equal(adapter.querySelector(root, selector), confirm);
assert.deepEqual(
  Array.from(
    adapter.getFastQueryDescendants(root, [
      new FastQuery.Text('Confirm', CompareOperator.Equal),
    ]),
  ),
  [confirm],
);
const all = adapter.querySelectorAll(root, Selector.compile('Button').value);
assert.ok(Array.isArray(all));
assert.deepEqual(all, [confirm, cancel]);
assert.throws(
  () => new MissingKeyNodeAdapter().querySelectorAll(
    root,
    Selector.compile('Button').value,
  ),
  /getNodeKey.*non-null/,
);

const traceSource = "@Root > Button[id='confirm']";
const traceSelector = Selector.parse(traceSource).value;
const trace = adapter.matchWithTrace(confirm, traceSelector);
assert.equal(trace?.target, root);
assert.ok(Array.isArray(trace?.units));
assert.ok(Array.isArray(trace?.units[0]?.steps));
assert.equal(trace?.units[0]?.steps[0]?.source, confirm);
assert.equal(trace?.units[0]?.steps[0]?.target, root);
assert.equal(trace?.units[0]?.steps[0]?.formattedRelation, '>');
const relationRange = trace?.units[0]?.steps[0]?.relationRange;
assert.equal(
  relationRange && traceSource.slice(relationRange.start, relationRange.end),
  '>',
);
const tracedAll = adapter.querySelectorAllWithTrace(
  root,
  Selector.parse('Button').value,
);
assert.ok(Array.isArray(tracedAll));
assert.deepEqual(
  tracedAll.map((result) => result.target),
  [confirm, cancel],
);

const trackingFastQueryAdapter = new TrackingFastQueryNodeAdapter();
assert.equal(
  trackingFastQueryAdapter.querySelector(root, selector, new MatchOptions(true)),
  confirm,
);
assert.equal(trackingFastQueryAdapter.fastQueryArgsWereArray, true);
assert.equal(trackingFastQueryAdapter.yieldedCount, 1);

const resumedFastQueryAdapter = new TrackingFastQueryNodeAdapter();
assert.equal(
  resumedFastQueryAdapter.querySelector(
    root,
    Selector.compile("Button[id='cancel']").value,
    new MatchOptions(true),
  ),
  cancel,
);
assert.equal(resumedFastQueryAdapter.yieldedCount, 2);

const exhaustiveFastQueryAdapter = new TrackingFastQueryNodeAdapter();
assert.deepEqual(
  exhaustiveFastQueryAdapter.querySelectorAll(
    root,
    Selector.compile(
      "(Button[id='confirm']) || (Button[id='cancel'])",
    ).value,
    new MatchOptions(true),
  ),
  [confirm, cancel],
);
assert.equal(exhaustiveFastQueryAdapter.yieldedCount, 2);

const defaultTypeModel = createDefaultSelectorTypeModel();
const defaultTypeResult = Selector.compile("Button[text.length=7]").value
  .validateType(defaultTypeModel);
assert.ok(defaultTypeResult instanceof SelectorTypeResult.Success);

const allTypeErrors = Selector.parse('Button[unknownA=1][unknownB=2]').value
  .getTypeErrors(defaultTypeModel);
assert.ok(Array.isArray(allTypeErrors));
assert.deepEqual(allTypeErrors.map((error) => error.expression), [
  'unknownA',
  'unknownB',
]);
assert.ok(allTypeErrors.every((error) => error.range !== null));

const typeBuilder = new JsSelectorTypeModelBuilder();
const customString = typeBuilder.type(JsSelectorTypeKind.String);
const customGlobal = typeBuilder.type(JsSelectorTypeKind.Object, 'global');
typeBuilder.property(customGlobal, 'title', customString);
const customModel = typeBuilder.build(customGlobal);
const customTypeResult = Selector.compile("Button[title='Confirm']").value
  .validateType(customModel);
assert.ok(customTypeResult instanceof SelectorTypeResult.Success);
