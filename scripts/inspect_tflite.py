import tflite
import sys

def inspect_model(model_path):
    print(f"=== Inspecting {model_path} ===")
    with open(model_path, 'rb') as f:
        buf = f.read()
    model = tflite.Model.GetRootAsModel(buf, 0)
    print(f"Version: {model.Version()}")
    print(f"Subgraphs count: {model.SubgraphsLength()}")
    
    for g_idx in range(model.SubgraphsLength()):
        graph = model.Subgraphs(g_idx)
        print(f"\n--- Subgraph {g_idx} ---")
        
        print("Inputs:")
        for i in range(graph.InputsLength()):
            tensor_idx = graph.Inputs(i)
            tensor = graph.Tensors(tensor_idx)
            name = tensor.Name().decode('utf-8') if tensor.Name() else f"tensor_{tensor_idx}"
            shape = [tensor.Shape(s) for s in range(tensor.ShapeLength())]
            dtype = tensor.Type()
            print(f"  Input[{i}] (tensor {tensor_idx}): name='{name}', shape={shape}, type={dtype}")
            
        print("Outputs:")
        for i in range(graph.OutputsLength()):
            tensor_idx = graph.Outputs(i)
            tensor = graph.Tensors(tensor_idx)
            name = tensor.Name().decode('utf-8') if tensor.Name() else f"tensor_{tensor_idx}"
            shape = [tensor.Shape(s) for s in range(tensor.ShapeLength())]
            dtype = tensor.Type()
            print(f"  Output[{i}] (tensor {tensor_idx}): name='{name}', shape={shape}, type={dtype}")

if __name__ == '__main__':
    path = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/assets/mobilefacenet.tflite'
    inspect_model(path)
