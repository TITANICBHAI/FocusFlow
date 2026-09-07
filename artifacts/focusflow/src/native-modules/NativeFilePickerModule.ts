import { NativeModules, Platform } from 'react-native';

const { NativeFilePicker } = NativeModules as {
  NativeFilePicker: {
    pickFile(mimeType: string): Promise<{ name: string; content: string } | null>;
    saveFile(content: string, fileName: string, mimeType: string): Promise<string | null>;
    readUri(uri: string): Promise<string | null>;
  };
};

export interface PickedFile {
  name: string;
  content: string;
}

export const NativeFilePickerModule = {
  /**
   * Opens the Android system file picker (ACTION_OPEN_DOCUMENT).
   * Returns { name, content } for the selected file, or null if cancelled.
   *
   * @param mimeType  e.g. "application/json" or "*\/*" for all files
   */
  async pickFile(mimeType = 'application/json'): Promise<PickedFile | null> {
    if (Platform.OS !== 'android') return null;
    if (!NativeFilePicker?.pickFile) return null;
    return NativeFilePicker.pickFile(mimeType);
  },

  /**
   * Opens the Android "Save to" dialog (ACTION_CREATE_DOCUMENT) so the user
   * can choose where to write the file — Downloads, Google Drive, etc.
   *
   * Returns the content URI string on success, or null if cancelled.
   *
   * @param content   UTF-8 text to write (e.g. JSON backup data)
   * @param fileName  Suggested filename shown in the dialog
   * @param mimeType  MIME type, e.g. "application/octet-stream"
   */
  async saveFile(
    content: string,
    fileName: string,
    mimeType = 'application/octet-stream',
  ): Promise<string | null> {
    if (Platform.OS !== 'android') return null;
    if (!NativeFilePicker?.saveFile) return null;
    return NativeFilePicker.saveFile(content, fileName, mimeType);
  },

  /**
   * Reads an Android content:// or file:// URI delivered by an ACTION_VIEW
   * intent. This must use the native ContentResolver path rather than
   * expo-file-system, because provider-backed documents are not app-local
   * filesystem paths.
   */
  async readUri(uri: string): Promise<string | null> {
    if (Platform.OS !== 'android') return null;
    if (!NativeFilePicker?.readUri) return null;
    return NativeFilePicker.readUri(uri);
  },
};
