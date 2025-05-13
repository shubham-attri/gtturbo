import json
import csv
from datetime import datetime, timedelta
import struct
import calendar
import os # New import for file system operations
import time # New import for polling interval

# --- Constants based on the UPDATED Arduino code & new info ---
MAX_READINGS_PER_PACKET = 50
NUM_CHANNELS = 4 # V, W, K, L
BYTES_PER_SENSOR_VALUE = 2 # uint16_t
POLLING_RATE_HZ = 18.0
MILLISECONDS_PER_READING_OFFSET = 1000.0 / POLLING_RATE_HZ

SIZE_OF_TIMESTAMP = 4
SIZE_OF_READINGS_BLOCK = MAX_READINGS_PER_PACKET * NUM_CHANNELS * BYTES_PER_SENSOR_VALUE # 50*4*2 = 400
SIZE_OF_FULL_DATAPACKET = SIZE_OF_TIMESTAMP + SIZE_OF_READINGS_BLOCK # 4 + 400 = 404 bytes

# NEW: Chunking information
BYTES_PER_BLE_CHUNK = 202
NUM_CHUNKS_PER_DATAPACKET = SIZE_OF_FULL_DATAPACKET / BYTES_PER_BLE_CHUNK # Should be 404 / 202 = 2

if SIZE_OF_FULL_DATAPACKET % BYTES_PER_BLE_CHUNK != 0:
    print(f"ERROR: SIZE_OF_FULL_DATAPACKET ({SIZE_OF_FULL_DATAPACKET}) is not perfectly divisible by BYTES_PER_BLE_CHUNK ({BYTES_PER_BLE_CHUNK}). Adjust constants.")
    exit()


def decode_device_timestamp(raw_timestamp_int, original_hex_for_debug="N/A"):
    """
    Decodes the custom 32-bit timestamp from the device into a datetime object.
    Uses clamping to handle potentially out-of-range bitfield values.
    """
    try:
        day_from_bits = (raw_timestamp_int >> 27) & 0x1F
        month_from_bits = (raw_timestamp_int >> 23) & 0x0F
        year_offset_from_bits = (raw_timestamp_int >> 18) & 0x1F
        hour_from_bits = (raw_timestamp_int >> 13) & 0x1F
        minute_from_bits = (raw_timestamp_int >> 7) & 0x3F
        second_from_bits = (raw_timestamp_int >> 1) & 0x3F

        year = 2000 + year_offset_from_bits
        
        month = month_from_bits
        if month == 0: month = 1 
        elif month > 12: month = 12
        
        day = day_from_bits
        if day == 0: day = 1
        try:
            max_days_in_month = calendar.monthrange(year, month)[1]
            if day > max_days_in_month: day = max_days_in_month
        except: day = 1 
        
        hour = hour_from_bits
        if hour > 23: hour = 23
        minute = minute_from_bits
        if minute > 59: minute = 59
        second = second_from_bits
        if second > 59: second = 59
        
        return datetime(year, month, day, hour, minute, second)
    except ValueError as e:
        print(f"Error creating datetime for ts_hex {original_hex_for_debug} (val 0x{raw_timestamp_int:08X}) after clamping: {e}. Clamped YMDHMS: {year}/{month}/{day} {hour}:{minute}:{second}")
        return None

def parse_reassembled_packet(packet_bytes, packet_number_for_log):
    """
    Parses a complete 404-byte DataPacket (byte array).
    Returns a list of CSV rows for this packet.
    """
    parsed_rows_for_this_packet = []

    if len(packet_bytes) != SIZE_OF_FULL_DATAPACKET:
        print(f"Error: Reassembled packet {packet_number_for_log} has incorrect size: {len(packet_bytes)}. Expected {SIZE_OF_FULL_DATAPACKET}. Skipping.")
        return []

    timestamp_bytes = packet_bytes[:SIZE_OF_TIMESTAMP]
    try:
        packet_timestamp_raw = struct.unpack('<I', timestamp_bytes)[0] 
    except struct.error as e:
        print(f"Error unpacking timestamp for reassembled packet {packet_number_for_log} from bytes {timestamp_bytes.hex()}: {e}. Skipping.")
        return []
        
    packet_start_datetime = decode_device_timestamp(packet_timestamp_raw, timestamp_bytes.hex())
    if packet_start_datetime is None:
        return []

    readings_bytes_data = packet_bytes[SIZE_OF_TIMESTAMP:]
    bytes_per_full_reading_set = NUM_CHANNELS * BYTES_PER_SENSOR_VALUE

    for i in range(MAX_READINGS_PER_PACKET):
        start_idx = i * bytes_per_full_reading_set
        end_idx = start_idx + bytes_per_full_reading_set

        if end_idx > len(readings_bytes_data):
            break
        
        current_reading_set_bytes = readings_bytes_data[start_idx:end_idx]
        sensor_values_scaled = []
        corrupt_reading_in_set = False

        bytes_per_channel_value = BYTES_PER_SENSOR_VALUE
        for ch_idx in range(NUM_CHANNELS):
            ch_start = ch_idx * bytes_per_channel_value
            ch_end = ch_start + bytes_per_channel_value
            channel_bytes = current_reading_set_bytes[ch_start:ch_end]

            try:
                raw_value = struct.unpack('<H', channel_bytes)[0]
            except struct.error as e:
                sensor_values_scaled.append(None)
                corrupt_reading_in_set = True
                continue
            
            if raw_value == 0xFFFF:
                sensor_values_scaled.append(None)
            else:
                sensor_values_scaled.append(raw_value / 10.0)
        
        if corrupt_reading_in_set and not any(v is not None for v in sensor_values_scaled):
            continue

        current_reading_datetime = packet_start_datetime + timedelta(milliseconds=(i * MILLISECONDS_PER_READING_OFFSET))
        
        row_data = [current_reading_datetime.strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]]
        row_data.extend(sensor_values_scaled)
        parsed_rows_for_this_packet.append(row_data)
        
    return parsed_rows_for_this_packet


def process_ble_notifications_fixed_chunks(json_file_path, csv_file_path):
    all_csv_rows = []
    reassembled_packet_count = 0
    skipped_notifications_count = 0
    
    with open(json_file_path, 'r') as f:
        ble_data = json.load(f)

    notifications = ble_data.get("notifications", [])
    num_total_notifications = len(notifications)

    print(f"Processing {num_total_notifications} notifications from device '{ble_data.get('device_name', 'N/A')}' for file '{os.path.basename(json_file_path)}'...") # Added filename for context
    print(f"Expecting full DataPacket size: {SIZE_OF_FULL_DATAPACKET} bytes.")
    print(f"Reassembling from {int(NUM_CHUNKS_PER_DATAPACKET)} chunks of {BYTES_PER_BLE_CHUNK} bytes each.") # Made NUM_CHUNKS_PER_DATAPACKET int for print

    i = 0
    while i <= num_total_notifications - NUM_CHUNKS_PER_DATAPACKET:
        current_packet_chunks_bytes = bytearray()
        current_packet_notification_numbers = []
        valid_chunks_for_packet = True

        for chunk_idx in range(int(NUM_CHUNKS_PER_DATAPACKET)):
            notification_index_in_json = i + chunk_idx
            notification_entry = notifications[notification_index_in_json]
            
            notification_number = notification_entry.get("notification_number")
            current_packet_notification_numbers.append(notification_number)
            hex_payload_chunk = notification_entry.get("notification_value")

            if not hex_payload_chunk or all(c == 'F' or c == 'f' for c in hex_payload_chunk):
                print(f"Warning: Notification {notification_number} (JSON index {notification_index_in_json}) is empty or all 'F's. Skipping this packet assembly.")
                skipped_notifications_count += 1
                valid_chunks_for_packet = False
                break 

            try:
                payload_bytes_chunk = bytes.fromhex(hex_payload_chunk)
            except ValueError:
                print(f"Warning: Notification {notification_number} (JSON index {notification_index_in_json}) has invalid hex '{hex_payload_chunk[:20]}...'. Skipping this packet assembly.")
                skipped_notifications_count += 1
                valid_chunks_for_packet = False
                break 

            if len(payload_bytes_chunk) != BYTES_PER_BLE_CHUNK:
                print(f"Warning: Notification {notification_number} (JSON index {notification_index_in_json}) has chunk size {len(payload_bytes_chunk)}, expected {BYTES_PER_BLE_CHUNK}. Skipping this packet assembly.")
                skipped_notifications_count += 1
                valid_chunks_for_packet = False
                break 
            
            current_packet_chunks_bytes.extend(payload_bytes_chunk)
        
        i += int(NUM_CHUNKS_PER_DATAPACKET) 

        if valid_chunks_for_packet and len(current_packet_chunks_bytes) == SIZE_OF_FULL_DATAPACKET:
            reassembled_packet_count += 1
            # print(f"  Reassembling packet #{reassembled_packet_count} from notifications {current_packet_notification_numbers}") # Kept commented as it can be verbose
            parsed_rows = parse_reassembled_packet(current_packet_chunks_bytes, reassembled_packet_count)
            all_csv_rows.extend(parsed_rows)
        elif valid_chunks_for_packet: 
             print(f"Warning: Assembled packet from notifications {current_packet_notification_numbers} has unexpected size {len(current_packet_chunks_bytes)}. Expected {SIZE_OF_FULL_DATAPACKET}.")


    if i < num_total_notifications:
        print(f"Warning: {num_total_notifications - i} notifications remaining at the end, not enough to form a full DataPacket for file '{os.path.basename(json_file_path)}'.")
    if skipped_notifications_count > 0:
        print(f"Skipped {skipped_notifications_count} individual notifications due to errors or being all 'F's for file '{os.path.basename(json_file_path)}'.")


    with open(csv_file_path, 'w', newline='') as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(['DeviceTimestamp', 'V', 'W', 'K', 'L'])
        writer.writerows(all_csv_rows)
    
    print(f"Successfully parsed {len(all_csv_rows)} individual sensor reading sets from {reassembled_packet_count} reassembled DataPackets for '{os.path.basename(json_file_path)}' and saved to '{csv_file_path}'")


if __name__ == "__main__":
    input_folder = "uploaded_files"
    output_folder = "processed_output"
    check_interval_seconds = 2  # Check for new files every 5 seconds
    processed_files = set() # Keep track of files already processed in this session

    print(f"--- BLE Data Processor Started ---")
    print(f"Monitoring folder: '{os.path.abspath(input_folder)}'")
    print(f"Outputting CSVs to: '{os.path.abspath(output_folder)}'")
    print(f"Checking for new files every {check_interval_seconds} seconds. Press Ctrl+C to stop.")

    # Create folders if they don't exist
    if not os.path.exists(input_folder):
        print(f"Input folder '{input_folder}' not found. Creating it.")
        os.makedirs(input_folder)
    if not os.path.exists(output_folder):
        print(f"Output folder '{output_folder}' not found. Creating it.")
        os.makedirs(output_folder)

    try:
        while True:
            # print(f"\n[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Scanning '{input_folder}' for new JSON files...")
            found_new_file_in_scan = False
            for filename in os.listdir(input_folder):
                if filename.endswith(".json") and filename not in processed_files:
                    found_new_file_in_scan = True
                    print(f"\n[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] New JSON file detected: {filename}")
                    
                    input_json_path = os.path.join(input_folder, filename)
                    # Sanitize filename for CSV output in case of unusual characters, though .replace is usually fine
                    base_name_no_ext = os.path.splitext(filename)[0]
                    output_csv_filename = f"{base_name_no_ext}.csv"
                    output_csv_path = os.path.join(output_folder, output_csv_filename)

                    print(f"Attempting to process: '{input_json_path}'")
                    
                    # Validate the discovered JSON file before full processing
                    try:
                        with open(input_json_path, 'r') as f_check:
                            # Try to load to catch JSON errors early
                            json.load(f_check) 
                        print(f"JSON file '{filename}' is valid. Starting full processing.")
                    except FileNotFoundError:
                        print(f"Error: JSON file '{filename}' seems to have been removed before processing. Skipping.")
                        processed_files.add(filename) # Add to avoid re-checking a deleted/moved file
                        continue
                    except json.JSONDecodeError as e:
                        print(f"Error: JSON file '{filename}' is not valid JSON: {e}. Skipping.")
                        processed_files.add(filename) # Mark as processed to avoid retrying a bad file
                        continue
                    except Exception as e:
                        print(f"Error during pre-check of '{filename}': {e}. Skipping.")
                        processed_files.add(filename)
                        continue
                    
                    # Process the valid JSON file
                    try:
                        process_ble_notifications_fixed_chunks(input_json_path, output_csv_path)
                        # process_ble_notifications_fixed_chunks already prints success message
                        processed_files.add(filename) # Mark as successfully processed
                    except Exception as e:
                        print(f"An critical error occurred while processing '{filename}': {e}")
                        print(f"File '{filename}' will be marked as processed to prevent repeated errors.")
                        processed_files.add(filename) # Mark to avoid retry loops on persistently problematic files
            
            # if not found_new_file_in_scan:
            #     print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] No new files found in '{input_folder}'. Waiting...")
                
            time.sleep(check_interval_seconds)

    except KeyboardInterrupt:
        print(f"\n[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Monitoring stopped by user. Exiting.")
    except Exception as e:
        print(f"\n[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] An unexpected error occurred during monitoring: {e}")
    finally:
        print("--- BLE Data Processor Finished ---")