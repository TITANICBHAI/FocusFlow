import { NativeModules, Platform } from 'react-native';

const { NativeFilePicker } = NativeModules as {
  NativeFilePicker: {
    pickFile(mimeType: string): Promise<{ name: string; content: string } | null>;
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
};
