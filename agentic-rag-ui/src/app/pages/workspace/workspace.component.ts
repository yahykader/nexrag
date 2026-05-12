import { Component } from '@angular/core';
import { UploadPageComponent } from '../../features/ingestion/pages/upload-page/upload-page.component';
import { ChatPageComponent } from '../../features/chat/pages/chat-page/chat-page.component';
import { ToastContainerComponent } from '../../shared/components/toast-container/toast-container.component';

@Component({
  selector: 'app-workspace',
  standalone: true,
  imports: [
    UploadPageComponent,
    ChatPageComponent,
    ToastContainerComponent
  ],
  template: `
    <div class="workspace-container">
      <aside class="workspace-sidebar">
        <div class="sidebar-content">
          <app-upload-page></app-upload-page>
        </div>
      </aside>
      <main class="workspace-main">
        <div class="main-content">
          <app-chat-page></app-chat-page>
        </div>
      </main>
      <app-toast-container></app-toast-container>
    </div>
  `,
  styles: [`
    .workspace-container {
      display: flex;
      height: 100vh;
    }
    .workspace-sidebar {
      width: 500px;
      overflow-y: auto;
      border-right: 1px solid #e0e0e0;
    }
    .sidebar-content {
      padding: 1rem;
    }
    .workspace-main {
      flex: 1;
      overflow-y: auto;
    }
    .main-content {
      padding: 1rem;
    }
  `]
})
export class WorkspaceComponent {}