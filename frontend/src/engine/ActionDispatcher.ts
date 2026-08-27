/**
 * Action Dispatcher
 *
 * Executes declarative actions defined in JSON page definitions
 */

import type { NavigateFunction } from '@tanstack/react-router';
import type { QueryClient } from '@tanstack/react-query';
import type { SupabaseClient } from '@supabase/supabase-js';
import type {
  ActionDefinition,
  SetStateAction,
  NavigateAction,
  ApiCallAction,
  RefetchAction,
  OpenModalAction,
  CloseModalAction,
  CustomAction,
  SequenceAction,
  ConditionalAction,
  ToggleArrayItemAction,
  ForEachAction,
  ExpressionContext,
} from './types';
import { evaluateExpression, resolveValue } from './ExpressionEvaluator';

/**
 * Custom action handler type
 */
export type CustomActionHandler = (
  payload: unknown,
  context: ExpressionContext
) => Promise<void> | void;

/**
 * Action dispatcher configuration
 */
export interface ActionDispatcherConfig {
  setState: (key: string, value: unknown) => void;
  navigate: NavigateFunction;
  supabase: SupabaseClient;
  queryClient: QueryClient;
  refetch: (sourceName: string) => void;
  openModal: (modalId: string, props?: Record<string, unknown>) => void;
  closeModal: (modalId?: string) => void;
  customHandlers?: Record<string, CustomActionHandler>;
}

/**
 * Create an action dispatcher
 */
export function createActionDispatcher(config: ActionDispatcherConfig) {
  const {
    setState,
    navigate,
    supabase,
    queryClient,
    refetch,
    openModal,
    closeModal,
    customHandlers = {},
  } = config;

  /**
   * Dispatch an action
   */
  async function dispatch(
    action: ActionDefinition,
    context: ExpressionContext
  ): Promise<void> {
    switch (action.action) {
      case 'setState':
        return handleSetState(action, context);

      case 'navigate':
        return handleNavigate(action, context);

      case 'apiCall':
        return handleApiCall(action, context);

      case 'refetch':
        return handleRefetch(action);

      case 'openModal':
        return handleOpenModal(action, context);

      case 'closeModal':
        return handleCloseModal(action);

      case 'custom':
        return handleCustom(action, context);

      case 'sequence':
        return handleSequence(action, context);

      case 'conditional':
        return handleConditional(action, context);

      case 'toggleArrayItem':
        return handleToggleArrayItem(action, context);

      case 'forEach':
        return handleForEach(action, context);

      default:
        console.warn(`Unknown action type: ${(action as { action: string }).action}`);
    }
  }

  /**
   * Handle setState action
   */
  function handleSetState(action: SetStateAction, context: ExpressionContext): void {
    const value = resolveValue(action.value, context);
    setState(action.key, value);
  }

  /**
   * Handle toggleArrayItem action (add value if absent, remove if present)
   */
  function handleToggleArrayItem(action: ToggleArrayItemAction, context: ExpressionContext): void {
    const value = resolveValue(action.value, context);
    const current = (context.state[action.key] as unknown[]) || [];
    const next = current.includes(value)
      ? current.filter((item) => item !== value)
      : [...current, value];
    setState(action.key, next);
  }

  /**
   * Handle navigate action
   */
  function handleNavigate(action: NavigateAction, context: ExpressionContext): void {
    const to = evaluateExpression(action.to, context) as string;
    navigate({ to, replace: action.replace });
  }

  /**
   * Handle apiCall action
   */
  async function handleApiCall(
    action: ApiCallAction,
    context: ExpressionContext
  ): Promise<void> {
    try {
      const data = resolveValue(action.data, context);
      const match = action.match
        ? (resolveValue(action.match, context) as Record<string, unknown>)
        : undefined;

      let result;

      switch (action.operation) {
        case 'insert':
          if (!action.table) throw new Error('Table required for insert');
          result = await supabase.from(action.table).insert(data);
          break;

        case 'update':
          if (!action.table) throw new Error('Table required for update');
          if (!match) throw new Error('Match criteria required for update');
          result = await supabase.from(action.table).update(data).match(match);
          break;

        case 'upsert':
          if (!action.table) throw new Error('Table required for upsert');
          result = await supabase.from(action.table).upsert(data as Record<string, unknown>[]);
          break;

        case 'delete': {
          if (!action.table) throw new Error('Table required for delete');
          if (!match) throw new Error('Match criteria required for delete');
          // Array values use .in() (e.g. bulk-deleting a set of selected ids);
          // scalar values use .eq(), equivalent to .match() for a single key.
          let deleteQuery = supabase.from(action.table).delete();
          for (const [key, value] of Object.entries(match)) {
            deleteQuery = Array.isArray(value)
              ? deleteQuery.in(key, value as unknown[])
              : deleteQuery.eq(key, value);
          }
          result = await deleteQuery;
          break;
        }

        case 'rpc':
          if (!action.function) throw new Error('Function name required for rpc');
          result = await supabase.rpc(action.function, data as Record<string, unknown>);
          break;

        case 'invoke':
          if (!action.function) throw new Error('Function name required for invoke');
          result = await supabase.functions.invoke(action.function, { body: data as Record<string, unknown> });
          break;

        case 'upload': {
          if (!action.bucket) throw new Error('Bucket required for upload');
          if (!action.path) throw new Error('Path required for upload');
          const path = evaluateExpression(action.path, context) as string;
          result = await supabase.storage.from(action.bucket).upload(path, data as Blob);
          break;
        }

        default:
          throw new Error(`Unknown API operation: ${action.operation}`);
      }

      if (result.error) {
        throw result.error;
      }

      // Invalidate queries for this table
      if (action.table) {
        queryClient.invalidateQueries({ queryKey: ['supabase', action.table] });
        queryClient.invalidateQueries({ queryKey: ['datasource'] });
      }

      // Execute onSuccess action (response payload available as event.data;
      // preserves any event fields already in context, e.g. from FileInput)
      if (action.onSuccess) {
        await dispatch(action.onSuccess, {
          ...context,
          event: { ...(context.event as object | undefined), data: result.data },
        });
      }
    } catch (error) {
      console.error('API call failed:', error);

      // Execute onError action
      if (action.onError) {
        await dispatch(action.onError, {
          ...context,
          event: { ...(context.event as object | undefined), error },
        });
      } else {
        throw error;
      }
    }
  }

  /**
   * Handle refetch action
   */
  function handleRefetch(action: RefetchAction): void {
    refetch(action.source);
  }

  /**
   * Handle openModal action
   */
  function handleOpenModal(
    action: OpenModalAction,
    context: ExpressionContext
  ): void {
    const props = action.props
      ? (resolveValue(action.props, context) as Record<string, unknown>)
      : undefined;
    openModal(action.modalId, props);
  }

  /**
   * Handle closeModal action
   */
  function handleCloseModal(action: CloseModalAction): void {
    closeModal(action.modalId);
  }

  /**
   * Handle custom action
   */
  async function handleCustom(
    action: CustomAction,
    context: ExpressionContext
  ): Promise<void> {
    const handler = customHandlers[action.handler];

    if (!handler) {
      console.warn(`Custom handler not found: ${action.handler}`);
      return;
    }

    const payload = action.payload
      ? resolveValue(action.payload, context)
      : undefined;

    await handler(payload, context);
  }

  /**
   * Handle sequence action (run multiple actions in order)
   */
  async function handleSequence(
    action: SequenceAction,
    context: ExpressionContext
  ): Promise<void> {
    for (const subAction of action.actions) {
      await dispatch(subAction, context);
    }
  }

  /**
   * Handle forEach action (run an action once per array item, sequentially;
   * stops and runs onError at the first failing item, matching apiCall's
   * onSuccess/onError convention)
   */
  async function handleForEach(
    action: ForEachAction,
    context: ExpressionContext
  ): Promise<void> {
    const items = resolveValue(action.items, context);
    if (!Array.isArray(items)) return;

    try {
      for (const item of items) {
        await dispatch(action.do, { ...context, [action.as]: item });
      }
    } catch (error) {
      if (action.onError) {
        await dispatch(action.onError, {
          ...context,
          event: { ...(context.event as object | undefined), error },
        });
        return;
      }
      throw error;
    }

    if (action.onSuccess) {
      await dispatch(action.onSuccess, context);
    }
  }

  /**
   * Handle conditional action
   */
  async function handleConditional(
    action: ConditionalAction,
    context: ExpressionContext
  ): Promise<void> {
    const condition = evaluateExpression(action.condition, context);

    if (condition) {
      await dispatch(action.then, context);
    } else if (action.else) {
      await dispatch(action.else, context);
    }
  }

  return { dispatch };
}

/**
 * Type for the dispatcher function
 */
export type ActionDispatch = (
  action: ActionDefinition,
  additionalContext?: Partial<ExpressionContext>
) => Promise<void>;
