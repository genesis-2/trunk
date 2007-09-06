/*++

Copyright (c) 1989 - 1999 Microsoft Corporation

Module Name:

    fileinfo.c

Abstract:

    This module implements the mini redirector call down routines pertaining to retrieval/
    update of file/directory/volume information.

--*/

#include "precomp.h"
#pragma hdrstop
#pragma warning(error:4101)   // Unreferenced local variable

//we turn away async operations that are not wait by posting. if we can wait
//then we turn off the sync flag so that things will just act synchronous
#define TURN_BACK_ASYNCHRONOUS_OPERATIONS() {                              \
    if (FlagOn(RxContext->Flags,RX_CONTEXT_FLAG_ASYNC_OPERATION)) {        \
        if (FlagOn(RxContext->Flags,RX_CONTEXT_FLAG_WAIT)) {               \
            ClearFlag(RxContext->Flags,RX_CONTEXT_FLAG_ASYNC_OPERATION);    \
        } else {                                                           \
            RxContext->PostRequest = TRUE;                                 \
            return(STATUS_PENDING);                                     \
        }                                                                  \
    }                                                                      \
  }

//
//  The local debug trace level
//

#define Dbg                              (DEBUG_TRACE_FILEINFO)

NTSTATUS
NulMRxQueryDirectory(
    IN OUT PRX_CONTEXT            RxContext
    )
/*++

Routine Description:

   This routine does a directory query. Only the NT-->NT path is implemented.

Arguments:

    RxContext - the RDBSS context

Return Value:

    NTSTATUS - The return status for the operation

--*/
{
    NTSTATUS Status = STATUS_INVALID_PARAMETER;
    FILE_INFORMATION_CLASS FileInformationClass;
    PVOID   Buffer;
    PULONG  pLengthRemaining;
	ULONG	CopySize;
	ULONG	BytesLeft;
	ULONG   TotalCopySize = 0;
	ULONG *FileIndex;
	WCHAR* fileName = L"sampleFile.txt";	
	WCHAR* fileName1 = L"sampleFile1.txt";	
	WCHAR* fileName2 = L"sampleFile2.txt";	
	
    FileInformationClass = RxContext->Info.FileInformationClass;
    Buffer = RxContext->Info.Buffer;
    pLengthRemaining = &RxContext->Info.LengthRemaining;
	BytesLeft = *pLengthRemaining;

	PAGED_CODE();

	TURN_BACK_ASYNCHRONOUS_OPERATIONS();

	DbgPrint("NulMRxQueryDirectory: ");

	RxDbgBreakPoint(82);

    switch (FileInformationClass)
    {
	    case FileDirectoryInformation:
		{			
	        PFILE_DIRECTORY_INFORMATION pDirInfo = (PFILE_DIRECTORY_INFORMATION) Buffer;			

			DbgPrint("FD Information \n");

			CopySize = sizeof( FILE_DIRECTORY_INFORMATION );									
			if (BytesLeft > CopySize )
			{
	            RtlZeroMemory( pDirInfo, CopySize );
				pDirInfo->NextEntryOffset = CopySize + CopySize % 8;
			    pDirInfo->FileAttributes = FILE_ATTRIBUTE_DIRECTORY;
			    pDirInfo->FileNameLength = sizeof( WCHAR );
			    pDirInfo->FileName[0] = L'.'; // one byte (no extra characters)				
				BytesLeft -= pDirInfo->NextEntryOffset;		
				TotalCopySize += pDirInfo->NextEntryOffset;		

				pDirInfo = (PFILE_DIRECTORY_INFORMATION)(((char*)pDirInfo) + pDirInfo->NextEntryOffset);	//move cursor

				//Add hardcoded FileName
				CopySize += wcslen(fileName1) - sizeof(WCHAR);  //skip first character
				if (BytesLeft > CopySize )
				{
					RtlZeroMemory( pDirInfo, sizeof( FILE_DIRECTORY_INFORMATION ));
					pDirInfo->FileAttributes = FILE_ATTRIBUTE_NORMAL;
					pDirInfo->FileNameLength = wcslen(fileName1);
					RtlCopyMemory(pDirInfo->FileName,fileName1, wcslen(fileName1));					
					
					TotalCopySize += CopySize;		 //Don't need to mod 8 for last entry
					*pLengthRemaining = TotalCopySize;
				}
				Status = RxContext->QueryDirectory.InitialQuery ?
					STATUS_SUCCESS : STATUS_NO_MORE_FILES;
			}
		}
		break;

	    case FileFullDirectoryInformation:
		{			
			DbgPrint("FFD Information \n");

			FileIndex = &RxContext->QueryDirectory.FileIndex;			

			if(RxContext->QueryDirectory.InitialQuery)
			{
				*FileIndex = 0;

			}		

			if(*FileIndex > 1){
				Status = STATUS_NO_MORE_FILES;				
			}

			if(*FileIndex == 0 && Status != STATUS_NO_MORE_FILES)
			{
				PFILE_FULL_DIR_INFORMATION pDirInfo = (PFILE_FULL_DIR_INFORMATION) Buffer;
				CopySize = sizeof( FILE_FULL_DIR_INFORMATION) + (wcslen(fileName1) - 1) * sizeof(WCHAR);  //skip first character;			
				if (BytesLeft > CopySize )
				{
					RtlZeroMemory( pDirInfo, sizeof( FILE_FULL_DIR_INFORMATION));
					pDirInfo->NextEntryOffset = CopySize + CopySize % 8;
					pDirInfo->FileAttributes = FILE_ATTRIBUTE_NORMAL | FILE_ATTRIBUTE_READONLY;
					pDirInfo->FileNameLength = wcslen(fileName1) * sizeof(WCHAR);
					RtlCopyMemory(pDirInfo->FileName, fileName1, wcslen(fileName1) * sizeof(WCHAR));		
					BytesLeft -= pDirInfo->NextEntryOffset;		
					TotalCopySize += pDirInfo->NextEntryOffset;							
					*FileIndex = 1; //set to next fileIndex

					if(RxContext->QueryDirectory.ReturnSingleEntry != 0){						
						*pLengthRemaining = TotalCopySize;
						Status = STATUS_SUCCESS;
					}
					else{
						((char*)Buffer) += pDirInfo->NextEntryOffset; // move pointer
					}
				}
				else{
					Status = STATUS_BUFFER_TOO_SMALL;
				}
			}
			if(*FileIndex == 1 && Status != STATUS_BUFFER_TOO_SMALL && Status != STATUS_SUCCESS)
			{		
				PFILE_FULL_DIR_INFORMATION pDirInfo = (PFILE_FULL_DIR_INFORMATION) Buffer;
				CopySize = sizeof( FILE_FULL_DIR_INFORMATION) + (wcslen(fileName2) - 1) * sizeof(WCHAR);  //skip first character;
				if (BytesLeft > CopySize )
				{
					RtlZeroMemory( pDirInfo, sizeof( FILE_FULL_DIR_INFORMATION));
					pDirInfo->NextEntryOffset = 0;  //Last entry
					pDirInfo->FileAttributes = FILE_ATTRIBUTE_NORMAL | FILE_ATTRIBUTE_READONLY;
					pDirInfo->FileNameLength = wcslen(fileName2) * sizeof(WCHAR);
					RtlCopyMemory(pDirInfo->FileName, fileName2, wcslen(fileName2) * sizeof(WCHAR));		
					BytesLeft -= CopySize;		
					TotalCopySize += CopySize;							

					*FileIndex = 2; //set to next fileIndex
					*pLengthRemaining = TotalCopySize;
					Status = STATUS_SUCCESS;
				}	
				else{
					if(TotalCopySize == 0){
						Status = STATUS_BUFFER_TOO_SMALL;
					}
				}
			}			
		}
        break;

		case FileBothDirectoryInformation:
		{		
	        PFILE_BOTH_DIR_INFORMATION pDirInfo = (PFILE_BOTH_DIR_INFORMATION) Buffer;

			DbgPrint("FBD Information \n");

			CopySize = sizeof( FILE_BOTH_DIR_INFORMATION );			
			if (BytesLeft > CopySize )
			{
	            RtlZeroMemory( pDirInfo, CopySize );
				pDirInfo->NextEntryOffset = CopySize + CopySize % 8;
			    pDirInfo->FileAttributes = FILE_ATTRIBUTE_DIRECTORY;
			    pDirInfo->FileNameLength = sizeof( WCHAR );
			    pDirInfo->FileName[0] = L'.'; // one byte (no extra characters)
				
				pDirInfo->ShortNameLength = sizeof( WCHAR );
				pDirInfo->ShortName[0] = L'.'; 
				
				BytesLeft -= pDirInfo->NextEntryOffset;		
				TotalCopySize += pDirInfo->NextEntryOffset;		

				pDirInfo = (PFILE_BOTH_DIR_INFORMATION)(((char*)pDirInfo) + pDirInfo->NextEntryOffset);	//move cursor

				//Add hardcoded FileName
				CopySize += wcslen(fileName) - sizeof(WCHAR);  //skip first character
				if (BytesLeft > CopySize )
				{
					RtlZeroMemory( pDirInfo, sizeof(FILE_BOTH_DIR_INFORMATION) );
					pDirInfo->FileAttributes = FILE_ATTRIBUTE_NORMAL;
					pDirInfo->FileNameLength = wcslen(fileName);
					RtlCopyMemory(pDirInfo->FileName,fileName, wcslen(fileName));

					pDirInfo->ShortNameLength = sizeof( WCHAR ) * 5;
					RtlCopyMemory(pDirInfo->ShortName, L"short", 5 * sizeof( WCHAR));
					
					TotalCopySize += CopySize;		 //Don't need to mod 8 for last entry
					*pLengthRemaining = TotalCopySize;
				}
				Status = RxContext->QueryDirectory.InitialQuery ?
					STATUS_SUCCESS : STATUS_NO_MORE_FILES;
			}
		}
        break;

		case FileNamesInformation:
		{			
	        PFILE_NAMES_INFORMATION pDirInfo = (PFILE_NAMES_INFORMATION) Buffer;

			DbgPrint("FN Information \n");

			CopySize = sizeof( FILE_NAMES_INFORMATION );
			
			if (BytesLeft > CopySize )
			{
	            RtlZeroMemory( pDirInfo, sizeof(FILE_NAMES_INFORMATION ));
				pDirInfo->NextEntryOffset = CopySize + CopySize % 4; //Only 4 byte for Names			    
			    pDirInfo->FileNameLength = sizeof( WCHAR );
			    pDirInfo->FileName[0] = L'.'; // one byte (no extra characters)
				BytesLeft -= pDirInfo->NextEntryOffset;		
				TotalCopySize += pDirInfo->NextEntryOffset;		

				pDirInfo = (PFILE_NAMES_INFORMATION)(((char*)pDirInfo) + pDirInfo->NextEntryOffset);	//move cursor

				//Add hardcoded FileName
				CopySize += wcslen(fileName) - sizeof(WCHAR);  //skip first character
				if (BytesLeft > CopySize )
				{
					RtlZeroMemory( pDirInfo, CopySize );					
					pDirInfo->FileNameLength = wcslen(fileName);
					RtlCopyMemory(pDirInfo->FileName,fileName, wcslen(fileName));
					
					TotalCopySize += CopySize;		 //Don't need to mod 8 for last entry
					*pLengthRemaining = TotalCopySize;
				}
				Status = RxContext->QueryDirectory.InitialQuery ?
					STATUS_SUCCESS : STATUS_NO_MORE_FILES;
			}
		}
        break;

		default:
			RxDbgTrace( 0, Dbg, ("NulMRxQueryDirectory: Invalid FS information class\n"));
			Status = STATUS_INVALID_PARAMETER;
			break;
	}

    return(Status);
}


NTSTATUS
NulMRxQueryVolumeInformation(
      IN OUT PRX_CONTEXT          RxContext
      )
/*++

Routine Description:

   This routine queries the volume information

Arguments:

    pRxContext         - the RDBSS context

    FsInformationClass - the kind of Fs information desired.

    pBuffer            - the buffer for copying the information

    pBufferLength      - the buffer length ( set to buffer length on input and set
                         to the remaining length on output)

Return Value:

    NTSTATUS - The return status for the operation

--*/
{
    NTSTATUS Status = STATUS_INVALID_PARAMETER;
    RxCaptureFcb;

    ULONG    OriginalLength = RxContext->Info.LengthRemaining;
    FS_INFORMATION_CLASS FsInformationClass = RxContext->Info.FsInformationClass;
    PVOID OriginalBuffer = RxContext->Info.Buffer;
    UNICODE_STRING ustrVolume;
    ULONG BytesToCopy;

    RxTraceEnter("NulMRxQueryVolumeInformation");

    switch( FsInformationClass ) {
        case FileFsVolumeInformation:
		{
    		PFILE_FS_VOLUME_INFORMATION pVolInfo = (PFILE_FS_VOLUME_INFORMATION) OriginalBuffer;
            RtlZeroMemory( pVolInfo, sizeof(FILE_FS_VOLUME_INFORMATION) );
            pVolInfo->VolumeCreationTime.QuadPart = 0;
            pVolInfo->VolumeSerialNumber = 0xBABAFACE;
            pVolInfo->VolumeLabelLength = sizeof(L"NULMRX");
            pVolInfo->SupportsObjects = FALSE;
            RtlInitUnicodeString( &ustrVolume, L"NULMRX" );

            RxContext->Info.LengthRemaining -= FIELD_OFFSET(FILE_FS_VOLUME_INFORMATION, VolumeLabel[0]);

            if (RxContext->Info.LengthRemaining >= (LONG)pVolInfo->VolumeLabelLength) {
                BytesToCopy = pVolInfo->VolumeLabelLength;
            } else {
                BytesToCopy = RxContext->Info.LengthRemaining;
            }

            RtlCopyMemory( &pVolInfo->VolumeLabel[0], (PVOID)ustrVolume.Buffer, BytesToCopy );
            RxContext->Info.LengthRemaining -= BytesToCopy;
            pVolInfo->VolumeLabelLength = BytesToCopy;
           
            Status = STATUS_SUCCESS;            
            DbgPrint("FileFsVolumeInformation\n");
		}
        break;
    
        case FileFsLabelInformation:
            DbgPrint("FileFsLabelInformation\n");
            break;
        
        case FileFsSizeInformation:
            DbgPrint("FileFsSizeInformation\n");
            break;
    
        case FileFsDeviceInformation:
            DbgPrint("FileFsDeviceInformation\n");
            break;
    
        case FileFsAttributeInformation:
		{
			PFILE_FS_ATTRIBUTE_INFORMATION pAttribInfo =
				(PFILE_FS_ATTRIBUTE_INFORMATION) OriginalBuffer;

			pAttribInfo->FileSystemAttributes = 0;
			pAttribInfo->MaximumComponentNameLength = 32;

            RxContext->Info.LengthRemaining -= FIELD_OFFSET(FILE_FS_ATTRIBUTE_INFORMATION, FileSystemName[0]);
			pAttribInfo->FileSystemNameLength = sizeof(L"SampleFS");
            if (RxContext->Info.LengthRemaining >= (LONG)pAttribInfo->FileSystemNameLength) {
                BytesToCopy = pAttribInfo->FileSystemNameLength;
            } else {
                BytesToCopy = RxContext->Info.LengthRemaining;
            }
            RtlCopyMemory( pAttribInfo->FileSystemName, L"SampleFS", BytesToCopy );
            RxContext->Info.LengthRemaining -= BytesToCopy;
			pAttribInfo->FileSystemNameLength = BytesToCopy;
           
            Status = STATUS_SUCCESS;            
            
            DbgPrint("FileFsAttributeInformation\n");
		}
        break;
    
        case FileFsControlInformation:
            DbgPrint("FileFsControlInformation\n");
            break;
    
        case FileFsFullSizeInformation:
            DbgPrint("FileFsFullSizeInformation\n");
            break;
    
        case FileFsObjectIdInformation:
            DbgPrint("FileFsObjectIdInformation\n");
            break;
    
        case FileFsMaximumInformation:
            DbgPrint("FileFsMaximumInformation\n");
            break;
    
        default:
            break;
    }

    RxTraceLeave(Status);
    return(Status);
}

NTSTATUS
NulMRxSetVolumeInformation(
      IN OUT PRX_CONTEXT              pRxContext
      )
/*++

Routine Description:

   This routine sets the volume information

Arguments:

    pRxContext - the RDBSS context

    FsInformationClass - the kind of Fs information desired.

    pBuffer            - the buffer for copying the information

    BufferLength       - the buffer length

Return Value:

    NTSTATUS - The return status for the operation

--*/
{
    NTSTATUS Status = STATUS_NOT_IMPLEMENTED;

    DbgPrint("NulMRxSetVolumeInformation \n");
    return(Status);
}


NTSTATUS
NulMRxQueryFileInformation(
      IN PRX_CONTEXT            RxContext
      )
/*++

Routine Description:

   This routine does a query file info. Only the NT-->NT path is implemented.

   The NT-->NT path works by just remoting the call basically without further ado.

Arguments:

    RxContext - the RDBSS context

Return Value:

    NTSTATUS - The return status for the operation

--*/
{
    NTSTATUS Status = STATUS_SUCCESS;
    RxCaptureFcb;
    FILE_INFORMATION_CLASS FunctionalityRequested = 
            RxContext->Info.FileInformationClass;
    PFILE_STANDARD_INFORMATION pFileStdInfo = 
            (PFILE_STANDARD_INFORMATION) RxContext->Info.Buffer;

    RxTraceEnter("NulMRxQueryFileInformation");

    switch( FunctionalityRequested ) {
        case FileBasicInformation:
            RxContext->Info.LengthRemaining -= sizeof(FILE_BASIC_INFORMATION);
            break;
    
        case FileInternalInformation:
            RxContext->Info.LengthRemaining -= sizeof(FILE_INTERNAL_INFORMATION);
            break;
            
        case FileEaInformation:
            RxContext->Info.LengthRemaining -= sizeof(FILE_EA_INFORMATION);
            break;
            
        case FileStandardInformation:

            RxDbgTrace(0, Dbg, ("FileSize is %d AllocationSize is %d\n",
                pFileStdInfo->EndOfFile.LowPart,pFileStdInfo->AllocationSize.LowPart));
            //(RxContext->CurrentIrp)->IoStatus.Information = sizeof(FILE_STANDARD_INFORMATION);
            RxContext->Info.LengthRemaining -= sizeof(FILE_STANDARD_INFORMATION);
            break;
        
        default:
            break;
    }
    
    RxTraceLeave(Status);
    return(Status);
}

NTSTATUS
NulMRxSetFileInformation(
      IN PRX_CONTEXT            RxContext
      )
/*++

Routine Description:

   This routine does a set file info. Only the NT-->NT path is implemented.

   The NT-->NT path works by just remoting the call basically without further ado.

Arguments:

    RxContext - the RDBSS context

Return Value:

    NTSTATUS - The return status for the operation

--*/
{
    NTSTATUS Status = STATUS_SUCCESS;
    RxCaptureFcb;
    FILE_INFORMATION_CLASS FunctionalityRequested = 
            RxContext->Info.FileInformationClass;
    PFILE_END_OF_FILE_INFORMATION pEndOfFileInfo = 
            (PFILE_END_OF_FILE_INFORMATION) RxContext->Info.Buffer;
    LARGE_INTEGER NewAllocationSize;

    RxTraceEnter("NulMRxSetFileInformation");

    switch( FunctionalityRequested ) {
        case FileBasicInformation:
            RxDbgTrace(0, Dbg, ("FileBasicInformation\n"));
            break;
    
        case FileDispositionInformation:
            RxDbgTrace(0, Dbg, ("FileDispositionInformation\n"));
            break;
    
        case FilePositionInformation:
            RxDbgTrace(0, Dbg, ("FilePositionInformation\n"));
            break;
    
        case FileAllocationInformation:
            RxDbgTrace(0, Dbg, ("FileAllocationInformation\n"));
            RxDbgTrace(0, Dbg, ("AllocSize is %d AllocSizeHigh is %d\n",
            pEndOfFileInfo->EndOfFile.LowPart,pEndOfFileInfo->EndOfFile.HighPart));
            break;
    
        case FileEndOfFileInformation:
            RxDbgTrace(0, Dbg, ("FileSize is %d FileSizeHigh is %d\n",
            capFcb->Header.AllocationSize.LowPart,capFcb->Header.AllocationSize.HighPart));

            if( pEndOfFileInfo->EndOfFile.QuadPart > 
                    capFcb->Header.AllocationSize.QuadPart ) {
            
                Status = NulMRxExtendFile(
                                RxContext,
                                &pEndOfFileInfo->EndOfFile,
                                &NewAllocationSize
                                );

                RxDbgTrace(0, Dbg, ("AllocSize is %d AllocSizeHigh is %d\n",
                            NewAllocationSize.LowPart,NewAllocationSize.HighPart));

                //
                //  Change the file allocation
                //
                capFcb->Header.AllocationSize.QuadPart = NewAllocationSize.QuadPart;
            } else {
                Status = NulMRxTruncateFile(
                                RxContext,
                                &pEndOfFileInfo->EndOfFile,
                                &NewAllocationSize
                                );
            }

            RxContext->Info.LengthRemaining -= sizeof(FILE_END_OF_FILE_INFORMATION);
            break;
    
        case FileRenameInformation:
            RxDbgTrace(0, Dbg, ("FileRenameInformation\n"));
            break;
    
        default:
            break;
    }
    
    RxTraceLeave(Status);
    return Status;
}

NTSTATUS
NulMRxSetFileInformationAtCleanup(
      IN PRX_CONTEXT            RxContext
      )
/*++

Routine Description:

   This routine sets the file information on cleanup. the old rdr just swallows this operation (i.e.
   it doesn't generate it). we are doing the same..........

Arguments:

    pRxContext           - the RDBSS context

Return Value:

    NTSTATUS - The return status for the operation

--*/
{
    NTSTATUS Status = STATUS_NOT_IMPLEMENTED;

    return(Status);
}


