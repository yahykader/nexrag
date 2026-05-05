import { UploadFile } from '../../store/ingestion/ingestion.state';
import { UploadProgress } from '../../../../core/services/websocket-progress.service';

export const mockUploadFile = (overrides: Partial<UploadFile> = {}): UploadFile => ({
  id: 'file-1',
  file: new File(['content'], 'doc.pdf', { type: 'application/pdf' }),
  progress: 0,
  status: 'pending',
  batchId: undefined,
  existingBatchId: undefined,
  retryAfterSeconds: undefined,
  ...overrides,
});

export const mockUploadProgress = (overrides: Partial<UploadProgress> = {}): UploadProgress => ({
  batchId: 'batch-1',
  filename: 'doc.pdf',
  stage: 'PROCESSING',
  progressPercentage: 50,
  message: 'Traitement en cours...',
  ...overrides,
});

export const mockRateLimitState = (overrides: Record<string, any> = {}) => ({
  rateLimit: {
    isRateLimited: false,
    retryAfterSeconds: 0,
    message: '',
    remainingTokens: { upload: null, batch: null, delete: null, search: null, default: null },
    limits: { upload: 10, batch: 5, delete: 20, search: 50, default: 30 },
    ...overrides,
  },
});

export const mockCrudState = (overrides: Record<string, any> = {}) => ({
  crud: {
    loading: false,
    activeDeleteOperations: 0,
    deleteOperations: [],
    duplicateChecks: {},
    batchInfos: {},
    systemStats: {},
    error: null,
    ...overrides,
  },
});

export const mockIngestionState = (uploads: UploadFile[] = []) => ({
  ingestion: {
    uploads,
    activeUploads: 0,
    stats: { total: 0, success: 0, errors: 0, duplicates: 0, rateLimited: 0 },
    strategies: [],
    activeIngestions: [],
    loading: false,
    error: null,
    uploadMode: 'async' as const,
  },
});

export const mockProgressState = () => ({
  progress: {
    connected: false,
    connecting: false,
    error: null,
    progressByBatch: {},
    subscribedBatches: [],
  },
});
